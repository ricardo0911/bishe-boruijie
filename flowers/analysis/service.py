from fastapi import FastAPI

from forecast_job import run_forecast_pipeline
from recommendation_job import run_recommendation_pipeline

app = FastAPI(title="Flower Shop Analysis Service", version="1.0.0")


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/forecast/run")
def run_forecast(days: int = 14, lead_time_days: int = 2, z_value: float = 1.28):
    summary = run_forecast_pipeline(
        days=days, lead_time_days=lead_time_days, z_value=z_value
    )
    return {
        "processedFlowers": summary.processed_flowers,
        "modelStat": summary.model_stat,
        "suggestionRows": summary.suggestion_rows,
    }


@app.post("/recommendation/run")
def run_recommendation(top_n: int = 5):
    summary = run_recommendation_pipeline(top_n=top_n)
    return {
        "usersProcessed": summary.users_processed,
        "rowsInserted": summary.rows_inserted,
    }
