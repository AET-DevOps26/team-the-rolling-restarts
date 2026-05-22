FROM python:3.12-slim

WORKDIR /app

COPY pyproject.toml README.md ./
RUN python -c 'import subprocess, tomllib; deps = tomllib.load(open("pyproject.toml","rb"))["project"]["dependencies"]; subprocess.check_call(["python","-m","pip","install","--no-cache-dir", *deps])'
COPY app/ ./app/
RUN pip install --no-cache-dir --no-deps .

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
