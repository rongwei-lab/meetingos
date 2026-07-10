#!/bin/sh
set -eu

# MinIO root 凭据只用于初始化。业务应用使用独立用户，并且只能访问配置的 bucket；
# 后续即使应用凭据泄露，也不能管理 MinIO 用户、策略或其他 bucket。
case "${MINIO_BUCKET:-}" in
  ''|*[!A-Za-z0-9_-]*)
    echo "ERROR: MINIO_BUCKET contains unsupported characters" >&2
    exit 1
    ;;
esac

[ -n "${MINIO_ROOT_USER:-}" ] || { echo "ERROR: MINIO_ROOT_USER is required" >&2; exit 1; }
[ -n "${MINIO_ROOT_PASSWORD:-}" ] || { echo "ERROR: MINIO_ROOT_PASSWORD is required" >&2; exit 1; }
[ -n "${MINIO_ACCESS_KEY:-}" ] || { echo "ERROR: MINIO_ACCESS_KEY is required" >&2; exit 1; }
[ -n "${MINIO_SECRET_KEY:-}" ] || { echo "ERROR: MINIO_SECRET_KEY is required" >&2; exit 1; }

if [ "$MINIO_ROOT_USER" = "$MINIO_ACCESS_KEY" ] || [ "$MINIO_ROOT_PASSWORD" = "$MINIO_SECRET_KEY" ]; then
  echo "ERROR: MinIO application credentials must differ from root credentials" >&2
  exit 1
fi

mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null
mc mb --ignore-existing "local/$MINIO_BUCKET" >/dev/null
mc anonymous set none "local/$MINIO_BUCKET" >/dev/null

POLICY_NAME="meetingos-${MINIO_BUCKET}-rw"
POLICY_FILE="/tmp/meetingos-minio-policy.json"
cat >"$POLICY_FILE" <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetBucketLocation",
        "s3:ListBucket",
        "s3:ListBucketMultipartUploads"
      ],
      "Resource": ["arn:aws:s3:::$MINIO_BUCKET"]
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:AbortMultipartUpload",
        "s3:DeleteObject",
        "s3:GetObject",
        "s3:ListMultipartUploadParts",
        "s3:PutObject"
      ],
      "Resource": ["arn:aws:s3:::$MINIO_BUCKET/*"]
    }
  ]
}
EOF

mc admin policy create local "$POLICY_NAME" "$POLICY_FILE" >/dev/null
# 从标准输入提供凭据，避免应用 secret 出现在短生命周期 init 容器的进程参数中。
printf '%s\n%s\n' "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" | mc admin user add local >/dev/null
mc admin policy attach local "$POLICY_NAME" --user "$MINIO_ACCESS_KEY" >/dev/null

echo "MinIO bucket and restricted application user are ready."
