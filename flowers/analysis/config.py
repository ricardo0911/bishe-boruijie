import os
from urllib.parse import quote_plus


def get_db_url() -> str:
    db_url = os.getenv("DB_URL")
    if db_url:
        return db_url

    user = quote_plus(os.getenv("DB_USERNAME", "root"))
    password = quote_plus(os.getenv("DB_PASSWORD", "root"))
    host = os.getenv("DB_HOST", "localhost")
    port = os.getenv("DB_PORT", "3306")
    name = os.getenv("DB_NAME", "flower_shop")
    return f"mysql+pymysql://{user}:{password}@{host}:{port}/{name}?charset=utf8mb4"
