FROM pytorch/pytorch:2.1.0-cuda12.1-cudnn8-runtime

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    libsndfile1 \
    libgl1-mesa-glx \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies
COPY inference-server/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy ML models package
COPY src/ /app/src/
COPY config.yaml /app/config.yaml
ENV PYTHONPATH="/app/src:${PYTHONPATH}"

# Copy server application
COPY inference-server/ /app/

# Expose port
EXPOSE 8000

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s \
  CMD curl -f http://localhost:8000/health || exit 1

# Run server
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
