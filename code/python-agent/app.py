"""Small internal HTTP service for the optional Python garden agent."""

from __future__ import annotations

import os
import secrets

from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

from garden_agent import review_record


class GardenRecord(BaseModel):
    emotion: str = Field(min_length=1, max_length=20)
    content: str = Field(min_length=1, max_length=255)
    trigger: str = Field(default="", max_length=64)


app = FastAPI(title="MindMan Python Garden Agent", version="0.1.0")


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.post("/v1/garden/insight")
def garden_insight(record: GardenRecord, x_agent_token: str | None = Header(default=None)) -> dict:
    expected = os.getenv("MINDMAN_PY_AGENT_TOKEN", "")
    if expected and not secrets.compare_digest(x_agent_token or "", expected):
        raise HTTPException(status_code=401, detail="Unauthorized")
    return review_record(record.model_dump())
