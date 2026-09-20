FROM python:3.12-slim
WORKDIR /app
COPY pyproject.toml README.md ./
COPY valuation_engine ./valuation_engine
RUN pip install --no-cache-dir .
ENV CACHE_DB=/data/edgar.sqlite REQUIRE_IDENTITY=true
VOLUME ["/data"]
EXPOSE 8000
CMD ["uvicorn", "valuation_engine.main:app", "--host", "0.0.0.0", "--port", "8000"]
