from sqlalchemy import create_engine

from config import get_db_url


def get_engine():
    return create_engine(get_db_url(), pool_pre_ping=True)
