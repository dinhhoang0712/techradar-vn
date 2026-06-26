import boto3
import os
import sys
from dotenv import load_dotenv

load_dotenv()
sys.stdout.reconfigure(encoding='utf-8')

# Cấu hình qua biến môi trường (có default hợp lý).
BUCKET_NAME = os.getenv("S3_BUCKET", "database-data-mining")
S3_PREFIX = os.getenv("S3_PREFIX", "extracted_data/")  # để "" nếu upload vào root
AWS_REGION = os.getenv("AWS_REGION") or os.getenv("AWS_DEFAULT_REGION")
LOCAL_FOLDER = os.path.join(os.path.dirname(__file__), "extracted_data")

s3 = boto3.client(
    "s3",
    region_name=AWS_REGION,  # None -> boto3 tự lấy từ ~/.aws/config nếu có
    aws_access_key_id=os.getenv("AWS_ACCESS_KEY_ID"),
    aws_secret_access_key=os.getenv("AWS_SECRET_ACCESS_KEY"),
)

# Thu thập danh sách file local trước khi đụng tới S3.
if not os.path.isdir(LOCAL_FOLDER):
    sys.exit(f"❌ Không tìm thấy thư mục local: {LOCAL_FOLDER}")

local_files = [
    f for f in os.listdir(LOCAL_FOLDER)
    if os.path.isfile(os.path.join(LOCAL_FOLDER, f)) and f.endswith(".json")
]

# Bảo vệ: không xoá sạch S3 khi không có file mới để thay thế (tránh mất data).
if not local_files:
    sys.exit(
        f"❌ Không có file .json nào trong {LOCAL_FOLDER}. "
        f"Dừng lại để tránh xoá sạch s3://{BUCKET_NAME}/{S3_PREFIX}."
    )

# 1. Xoá các file cũ trong folder trên S3
print(f"Xoá các file cũ trong s3://{BUCKET_NAME}/{S3_PREFIX} ...")
objects_to_delete = s3.list_objects_v2(Bucket=BUCKET_NAME, Prefix=S3_PREFIX)

if 'Contents' in objects_to_delete:
    delete_keys = [{'Key': obj['Key']} for obj in objects_to_delete['Contents']]
    s3.delete_objects(Bucket=BUCKET_NAME, Delete={'Objects': delete_keys})
    print(f"  -> Đã xoá {len(delete_keys)} file(s).")
else:
    print("  -> Không có file cũ nào để xoá.")

print("\nBắt đầu upload file mới...")
# 2. Upload các file mới
uploaded_count = 0
for filename in local_files:
    filepath = os.path.join(LOCAL_FOLDER, filename)
    s3_key = S3_PREFIX + filename
    print(f"Uploading {filename} -> s3://{BUCKET_NAME}/{s3_key} ...", end=" ")
    s3.upload_file(filepath, BUCKET_NAME, s3_key)
    uploaded_count += 1
    print("Done!")

print(f"\nAll {uploaded_count} files uploaded successfully!")
