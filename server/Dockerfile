# Tibeb server image. Built and run by deploy/docker-compose.yml (see docs/DEPLOY.md).
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 USAGE_DB=/data/usage.db
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt \
 && useradd --system --uid 10001 --home /app tibeb \
 && mkdir /data && chown tibeb /data
COPY --chown=root:root *.py ./
RUN rm -f test_*.py conftest.py

USER tibeb
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8080/healthz',timeout=4).status==200 else 1)"
# --proxy-headers: the admin lockout and rate limits see the real client IP that Caddy forwards.
# Port 8080 is never published to the internet (only Caddy can reach it), so trusting its headers is safe.
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8080", "--proxy-headers", "--forwarded-allow-ips", "*", "--timeout-keep-alive", "75"]
