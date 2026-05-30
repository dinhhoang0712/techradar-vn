# Tạo Relationships giữa các Node trong Neo4j

## Vấn đề hiện tại
Các node **Job, Company, Person** trong Neo4j chưa có liên kết với nhau.

## Giải pháp

### Cách 1: Chạy script tạo relationships trực tiếp

Sử dụng script `create_relationships.py`:

```bash
cd src/database/utils
python create_relationships.py
```

Script sẽ tạo các relationships sau:

1. **Job → Company** (HIRES_FOR)
   - Các vị trí công việc được tuyển dụng bởi công ty nào

2. **Job → Technology** (REQUIRES)
   - Các công việc yêu cầu công nghệ/kỹ năng nào

3. **Job → Skill** (REQUIRES)
   - Các công việc yêu cầu kỹ năng cụ thể nào

4. **Company → Technology** (USES)
   - Các công ty sử dụng công nghệ nào

5. **Person → Company** (WORKS_AT)
   - Các người làm việc cho công ty nào

6. **Article → Technology** (MENTIONS)
   - Các bài viết nhắc đến công nghệ nào

7. **Article → Company** (MENTIONS)
   - Các bài viết nhắc đến công ty nào

### Cách 2: Chạy pipeline hoàn chỉnh

Sử dụng script `run_complete_pipeline.py`:

```bash
cd src/database/utils
python run_complete_pipeline.py
```

Script sẽ:
1. Import tất cả dữ liệu từ 3 nguồn
2. Tạo tất cả relationships
3. In ra thống kê cuối cùng

## Kết quả mong đợi

```
📊 FINAL DATABASE STATISTICS
   Article        :    66
   Technology     :    170
   Company        :    46
   Skill          :    135
   Person         :    9
   Job            :    39
   Relationships  :    400+
```

## Kiểm tra relationships đã tạo

Trong Neo4j Browser, chạy:

```cypher
// Kiểm tra Job → Company relationships
MATCH (j:Job)-[r:HIRES_FOR]->(c:Company)
RETURN j.title, c.name, type(r)

// Kiểm tra Job → Technology relationships
MATCH (j:Job)-[r:REQUIRES]->(t:Technology)
RETURN j.title, t.name, type(r), properties(r)

// Kiểm tra Company → Technology relationships
MATCH (c:Company)-[r:USES]->(t:Technology)
RETURN c.name, t.name, type(r)

// Kiểm tra tất cả relationships
MATCH ()-[r]->()
RETURN type(r) as rel_type, count(r) as count
ORDER BY rel_type
```

## Tham khảo

Để hiểu rõ hơn về relationships theo schema trong `Mo_ta_database_cho_RAG.md`, xem:

```
src/database/Mo_ta_database_cho_RAG.md
```

Các relationships được mô tả chi tiết ở phần "3. Relationship Definitions (Mối quan hệ)".

---

**Created**: April 6, 2026
**Status**: ✅ Ready to use
