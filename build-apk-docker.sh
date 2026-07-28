#!/usr/bin/env bash
set -e

IMAGE_NAME="c3nav-android-builder"
OUTPUT_DIR="./build-output"

echo "Building Docker image ${IMAGE_NAME}..."
docker build -t "${IMAGE_NAME}" .

echo "Extracting APK from Docker container..."
mkdir -p "${OUTPUT_DIR}"
docker run --rm -v "$(pwd)/${OUTPUT_DIR}:/output" "${IMAGE_NAME}"

echo "APK successfully built and saved to ${OUTPUT_DIR}/app-debug.apk"
