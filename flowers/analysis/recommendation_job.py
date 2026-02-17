from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, List

import pandas as pd
from sqlalchemy import text

from db import get_engine


@dataclass
class RecommendationSummary:
    users_processed: int
    rows_inserted: int


def load_top_sales(engine) -> pd.DataFrame:
    sql = """
    SELECT oi.product_id, SUM(oi.quantity) AS sales_qty
    FROM order_item oi
    JOIN customer_order o ON oi.order_id = o.id
    WHERE o.status IN ('PAID', 'COMPLETED')
      AND o.pay_time >= DATE_SUB(NOW(), INTERVAL 30 DAY)
    GROUP BY oi.product_id
    """
    return pd.read_sql(sql, engine)


def load_user_preference(engine) -> pd.DataFrame:
    sql = """
    SELECT user_id, category, cnt
    FROM (
      SELECT o.user_id, p.category, COUNT(*) AS cnt,
             ROW_NUMBER() OVER (PARTITION BY o.user_id ORDER BY COUNT(*) DESC, p.category) AS rn
      FROM customer_order o
      JOIN order_item oi ON oi.order_id = o.id
      JOIN product p ON oi.product_id = p.id
      WHERE o.status IN ('PAID', 'COMPLETED')
      GROUP BY o.user_id, p.category
    ) t
    WHERE rn = 1
    """
    return pd.read_sql(sql, engine)


def load_candidate_products(engine) -> pd.DataFrame:
    sql = """
    SELECT
      p.id AS product_id,
      p.title,
      p.category,
      COALESCE(MIN(st.available_qty), 0) AS min_material_available
    FROM product p
    LEFT JOIN product_bom b ON p.id = b.product_id
    LEFT JOIN (
      SELECT flower_id, SUM(current_qty - locked_qty) AS available_qty
      FROM inventory_batch
      GROUP BY flower_id
    ) st ON b.flower_id = st.flower_id
    WHERE p.status = 'ON_SALE'
    GROUP BY p.id, p.title, p.category
    """
    return pd.read_sql(sql, engine)


def load_users(engine) -> pd.DataFrame:
    return pd.read_sql("SELECT id AS user_id FROM user_customer", engine)


def build_recommendations_for_user(
    user_id: int,
    user_pref_category: str | None,
    candidates: pd.DataFrame,
    sales_map: Dict[int, float],
    max_sales: float,
    top_n: int,
) -> List[dict]:
    rows: List[dict] = []

    for r in candidates.itertuples(index=False):
        product_id = int(r.product_id)
        category = str(r.category)
        available = float(r.min_material_available or 0.0)

        sales_qty = float(sales_map.get(product_id, 0.0))
        sales_score = 0.0 if max_sales <= 0 else sales_qty / max_sales
        category_score = (
            1.0 if user_pref_category and category == user_pref_category else 0.0
        )
        stock_score = min(1.0, max(0.0, available / 100.0))

        score = 0.50 * sales_score + 0.30 * category_score + 0.20 * stock_score
        reason = []
        if category_score > 0:
            reason.append("匹配用户偏好分类")
        if sales_score > 0.4:
            reason.append("近30天热销")
        if stock_score > 0.4:
            reason.append("库存充足")
        if not reason:
            reason.append("综合推荐")

        rows.append(
            {
                "user_id": user_id,
                "product_id": product_id,
                "score": round(score, 4),
                "reason": "，".join(reason),
            }
        )

    rows.sort(key=lambda x: x["score"], reverse=True)
    return rows[:top_n]


def run_recommendation_pipeline(top_n: int = 5) -> RecommendationSummary:
    engine = get_engine()

    users_df = load_users(engine)
    candidates_df = load_candidate_products(engine)
    top_sales_df = load_top_sales(engine)
    user_pref_df = load_user_preference(engine)

    sales_map = {
        int(r.product_id): float(r.sales_qty)
        for r in top_sales_df.itertuples(index=False)
    }
    max_sales = max(sales_map.values(), default=0.0)

    pref_map = {
        int(r.user_id): str(r.category) for r in user_pref_df.itertuples(index=False)
    }

    delete_sql = text("DELETE FROM recommendation_result WHERE user_id = :user_id")
    insert_sql = text(
        """
        INSERT INTO recommendation_result(user_id, product_id, score, reason, generated_at)
        VALUES (:user_id, :product_id, :score, :reason, NOW())
        """
    )

    rows_inserted = 0
    users_processed = 0

    with engine.begin() as conn:
        for u in users_df.itertuples(index=False):
            user_id = int(u.user_id)
            user_pref = pref_map.get(user_id)

            recs = build_recommendations_for_user(
                user_id=user_id,
                user_pref_category=user_pref,
                candidates=candidates_df,
                sales_map=sales_map,
                max_sales=max_sales,
                top_n=top_n,
            )

            conn.execute(delete_sql, {"user_id": user_id})
            for rec in recs:
                conn.execute(insert_sql, rec)
                rows_inserted += 1

            users_processed += 1

    return RecommendationSummary(
        users_processed=users_processed, rows_inserted=rows_inserted
    )


if __name__ == "__main__":
    summary = run_recommendation_pipeline(top_n=5)
    print(
        {
            "users_processed": summary.users_processed,
            "rows_inserted": summary.rows_inserted,
        }
    )
