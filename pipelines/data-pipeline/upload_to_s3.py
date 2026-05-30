import boto3
import os
import sys
from dotenv import load_dotenv

load_dotenv()
sys.stdout.reconfigure(encoding='utf-8')

s3 = boto3.client(
    "s3",
    aws_access_key_id=os.getenv("AWS_ACCESS_KEY_ID"),
    aws_secret_access_key=os.getenv("AWS_SECRET_ACCESS_KEY")
)

#BUCKET_NAME = "raw-data-kpdl"
BUCKET_NAME = "database-data-mining"
LOCAL_FOLDER = os.path.join(os.path.dirname(__file__), "extracted_data")
S3_PREFIX = "extracted_data/"  # Thư mục trên S3, để "" nếu muốn upload vào root

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
for filename in os.listdir(LOCAL_FOLDER):
    filepath = os.path.join(LOCAL_FOLDER, filename)
    if os.path.isfile(filepath) and filename.endswith(".json"):
        s3_key = S3_PREFIX + filename
        print(f"Uploading {filename} -> s3://{BUCKET_NAME}/{s3_key} ...", end=" ")
        s3.upload_file(filepath, BUCKET_NAME, s3_key)
        uploaded_count += 1
        print("Done!")

print(f"\nAll {uploaded_count} files uploaded successfully!")
