<?php
header('Content-Type: application/json; charset=utf-8');
require_once 'db_config.php'; // 确保路径正确，引入数据库连接 $conn

try {
    // 查询所有产品，按 ID 倒序排列（最新的在最上面）
    // 注意：这里将数据库的 description 映射为 Kotlin 模型中的 desc
    $sql = "SELECT 
                id, 
                name, 
                deadline, 
                spec, 
                description as `desc`, 
                images 
            FROM gifts 
            ORDER BY id DESC";

    $result = $conn->query($sql);

    $gifts = [];
    if ($result) {
        while ($row = $result->fetch_assoc()) {
            // 处理 ID 类型
            $row['id'] = (int)$row['id'];
            
            // 处理图片字段：
            // 如果数据库存的是 JSON 字符串，解析它；如果是空则给空数组
            if (!empty($row['images'])) {
                $decoded_images = json_decode($row['images'], true);
                $row['images'] = is_array($decoded_images) ? $decoded_images : [];
            } else {
                $row['images'] = [];
            }
            
            $gifts[] = $row;
        }
    }

    // 直接输出数组，Retrofit 会将其解析为 List<AdminGift>
    echo json_encode($gifts, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);

} catch (Exception $e) {
    http_response_code(500);
    echo json_encode(["error" => $e->getMessage()]);
}

$conn->close();
?>