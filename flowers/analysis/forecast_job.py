from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta
from math import sqrt
from typing import Dict, Tuple

import pandas as pd
from sqlalchemy import text

from db import get_engine

try:
    from prophet import Prophet

    PROPHET_AVAILABLE = True
except Exception:
    Prophet = None
    PROPHET_AVAILABLE = False


@dataclass
class ForecastSummary:
    processed_flowers: int
    model_stat: Dict[str, int]
    suggestion_rows: int


def load_daily_usage(engine) -> pd.DataFrame:
    sql = """
    SELECT
        flower_id,
        DATE(updated_at) AS ds,
        SUM(lock_qty) AS y
    FROM stock_lock
    WHERE status = 'CONFIRMED'
    GROUP BY flower_id, DATE(updated_at)
    ORDER BY flower_id, ds
    """
    df = pd.read_sql(sql, engine)
    if df.empty:
        return pd.DataFrame(columns=["flower_id", "ds", "y"])
    df["ds"] = pd.to_datetime(df["ds"])
    df["y"] = pd.to_numeric(df["y"], errors="coerce").fillna(0.0)
    return df


def predict_with_prophet(df: pd.DataFrame, days: int) -> Tuple[pd.DataFrame, str]:
    if Prophet is None:
        raise RuntimeError("prophet is not available")

    model = Prophet(
        weekly_seasonality=True, yearly_seasonality=True, daily_seasonality=False
    )
    try:
        model.add_country_holidays(country_name="CN")
    except Exception:
        pass

    model.fit(df[["ds", "y"]])
    future = model.make_future_dataframe(periods=days, freq="D")
    fc = model.predict(future).tail(days)
    output = fc[["ds", "yhat", "yhat_lower", "yhat_upper"]].copy()
    output.rename(
        columns={
            "yhat": "predicted_sales",
            "yhat_lower": "confidence_lower",
            "yhat_upper": "confidence_upper",
        },
        inplace=True,
    )
    output["predicted_sales"] = output["predicted_sales"].clip(lower=0)
    output["confidence_lower"] = output["confidence_lower"].clip(lower=0)
    output["confidence_upper"] = output["confidence_upper"].clip(lower=0)
    return output, "prophet"


def predict_with_moving_average(
    df: pd.DataFrame, days: int
) -> Tuple[pd.DataFrame, str]:
    if not df.empty:
        avg = float(df["y"].tail(14).mean())
        std = float(df["y"].tail(30).std(ddof=1)) if len(df) > 1 else 0.0
        start_date = (df["ds"].max() + pd.Timedelta(days=1)).normalize()
    else:
        avg = 0.0
        std = 0.0
        start_date = pd.Timestamp.today().normalize() + pd.Timedelta(days=1)

    avg = max(0.0, avg)
    lower = max(0.0, avg - std)
    upper = avg + std

    dates = pd.date_range(start=start_date, periods=days, freq="D")
    output = pd.DataFrame(
        {
            "ds": dates,
            "predicted_sales": [avg] * days,
            "confidence_lower": [lower] * days,
            "confidence_upper": [upper] * days,
        }
    )
    return output, "moving_average"


def upsert_forecast(
    engine, flower_id: int, forecast_df: pd.DataFrame, model_name: str
) -> None:
    insert_sql = text(
        """
        INSERT INTO forecast_result(
            flower_id, forecast_date, predicted_sales, confidence_lower, confidence_upper, model_name, generated_at
        ) VALUES (
            :flower_id, :forecast_date, :predicted_sales, :confidence_lower, :confidence_upper, :model_name, NOW()
        )
        ON DUPLICATE KEY UPDATE
            predicted_sales = VALUES(predicted_sales),
            confidence_lower = VALUES(confidence_lower),
            confidence_upper = VALUES(confidence_upper),
            model_name = VALUES(model_name),
            generated_at = NOW()
        """
    )

    with engine.begin() as conn:
        for row in forecast_df.itertuples(index=False):
            conn.execute(
                insert_sql,
                {
                    "flower_id": int(flower_id),
                    "forecast_date": row.ds.date(),
                    "predicted_sales": round(float(row.predicted_sales), 2),
                    "confidence_lower": round(float(row.confidence_lower), 2),
                    "confidence_upper": round(float(row.confidence_upper), 2),
                    "model_name": model_name,
                },
            )


def upsert_replenishment_suggestions(
    engine, horizon_days: int, lead_time_days: int, z_value: float
) -> int:
    today = date.today()
    end_date = today + timedelta(days=horizon_days - 1)

    forecast_sql = text(
        """
        SELECT flower_id,
               SUM(predicted_sales) AS demand_sum,
               AVG(predicted_sales) AS avg_daily
        FROM forecast_result
        WHERE forecast_date BETWEEN :start_date AND :end_date
        GROUP BY flower_id
        """
    )
    on_hand_sql = text(
        """
        SELECT flower_id,
               ROUND(COALESCE(SUM(current_qty - locked_qty), 0), 2) AS on_hand
        FROM inventory_batch
        GROUP BY flower_id
        """
    )
    std_sql = text(
        """
        SELECT flower_id,
               COALESCE(STDDEV_SAMP(daily_qty), 0) AS sigma
        FROM (
            SELECT flower_id, DATE(updated_at) AS ds, SUM(lock_qty) AS daily_qty
            FROM stock_lock
            WHERE status = 'CONFIRMED'
            GROUP BY flower_id, DATE(updated_at)
        ) t
        GROUP BY flower_id
        """
    )

    upsert_sql = text(
        """
        INSERT INTO replenishment_suggestion(
            flower_id, suggestion_date, predicted_demand, safety_stock, reorder_point,
            on_hand, in_transit, suggested_qty, status, generated_at
        ) VALUES (
            :flower_id, :suggestion_date, :predicted_demand, :safety_stock, :reorder_point,
            :on_hand, 0, :suggested_qty, 'NEW', NOW()
        )
        ON DUPLICATE KEY UPDATE
            predicted_demand = VALUES(predicted_demand),
            safety_stock = VALUES(safety_stock),
            reorder_point = VALUES(reorder_point),
            on_hand = VALUES(on_hand),
            suggested_qty = VALUES(suggested_qty),
            status = 'NEW',
            generated_at = NOW()
        """
    )

    with engine.begin() as conn:
        forecast_rows = (
            conn.execute(forecast_sql, {"start_date": today, "end_date": end_date})
            .mappings()
            .all()
        )
        on_hand_rows = conn.execute(on_hand_sql).mappings().all()
        std_rows = conn.execute(std_sql).mappings().all()

        on_hand_map = {
            int(r["flower_id"]): float(r["on_hand"] or 0.0) for r in on_hand_rows
        }
        sigma_map = {int(r["flower_id"]): float(r["sigma"] or 0.0) for r in std_rows}

        row_count = 0
        for row in forecast_rows:
            flower_id = int(row["flower_id"])
            predicted_demand = float(row["demand_sum"] or 0.0)
            avg_daily = float(row["avg_daily"] or 0.0)
            sigma = sigma_map.get(flower_id, 0.0)
            on_hand = on_hand_map.get(flower_id, 0.0)

            safety_stock = z_value * sigma * sqrt(max(1, lead_time_days))
            reorder_point = avg_daily * lead_time_days + safety_stock
            suggested_qty = max(0.0, predicted_demand + safety_stock - on_hand)

            conn.execute(
                upsert_sql,
                {
                    "flower_id": flower_id,
                    "suggestion_date": today,
                    "predicted_demand": round(predicted_demand, 2),
                    "safety_stock": round(safety_stock, 2),
                    "reorder_point": round(reorder_point, 2),
                    "on_hand": round(on_hand, 2),
                    "suggested_qty": round(suggested_qty, 2),
                },
            )
            row_count += 1

        return row_count


def run_forecast_pipeline(
    days: int = 14, lead_time_days: int = 2, z_value: float = 1.28
) -> ForecastSummary:
    engine = get_engine()
    usage_df = load_daily_usage(engine)

    if usage_df.empty:
        return ForecastSummary(processed_flowers=0, model_stat={}, suggestion_rows=0)

    model_stat: Dict[str, int] = {}
    processed = 0

    for flower_id, group in usage_df.groupby("flower_id"):
        group = group.sort_values("ds")

        model_name = "moving_average"
        if PROPHET_AVAILABLE and len(group) >= 30:
            try:
                forecast_df, model_name = predict_with_prophet(group, days)
            except Exception:
                forecast_df, model_name = predict_with_moving_average(group, days)
        else:
            forecast_df, model_name = predict_with_moving_average(group, days)

        upsert_forecast(engine, int(flower_id), forecast_df, model_name)
        model_stat[model_name] = model_stat.get(model_name, 0) + 1
        processed += 1

    suggestion_rows = upsert_replenishment_suggestions(
        engine, horizon_days=days, lead_time_days=lead_time_days, z_value=z_value
    )
    return ForecastSummary(
        processed_flowers=processed,
        model_stat=model_stat,
        suggestion_rows=suggestion_rows,
    )


if __name__ == "__main__":
    result = run_forecast_pipeline(days=14)
    print(
        {
            "processed_flowers": result.processed_flowers,
            "model_stat": result.model_stat,
            "suggestion_rows": result.suggestion_rows,
        }
    )
