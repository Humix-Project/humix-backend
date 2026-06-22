FROM python:3.11-slim

WORKDIR /app

# Suppress interactive prompts during package installation
ENV DEBIAN_FRONTEND=noninteractive

# Install system dependencies (ffmpeg is required by librosa to load audio files)
RUN apt-get update && apt-get install -y \
    ffmpeg \
    && rm -rf /var/lib/apt/lists/*

# Copy and install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy source code files
COPY vector_processor.py .
COPY app_cpu.py .

EXPOSE 8000

# Start FastAPI application
CMD ["uvicorn", "app_cpu:app", "--host", "0.0.0.0", "--port", "8000"]
