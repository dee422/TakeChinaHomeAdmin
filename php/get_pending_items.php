<?php
// 开启错误显示以便调试
ini_set('display_errors', 1);
error_reporting(E_ALL);

header('Content-Type: application/json; charset=utf-8');
require_once 'db_config.php';

// 使用数据库实际字段名 并起别名匹配 Kotlin 模型
$sql = "SELECT 
            id, 
            item_name AS itemName, 
            description, 
            image_url AS imageUrl, 
            owner_email AS ownerEmail, 
            contact_code AS contactCode, 
            exchange_wish AS exchangeWish, 
            status 
        FROM swap_items 
        WHERE status = 1 
        ORDER BY id DESC";

$result = $conn->query($sql);

$items = [];
if ($result) {
    while($row = $result->fetch_assoc()) {
        // 强制转换数值类型
        $row['id'] = (int)$row['id'];
        $row['status'] = (int)$row['status'];
        $row['exchangeWish'] = (int)$row['exchangeWish'];
        $items[] = $row;
    }
    echo json_encode([
        "success" => true,
        "message" => "Success",
        "data" => $items
    ]);
} else {
    // 如果 SQL 报错，这里会输出具体原因
    echo json_encode([
        "success" => false, 
        "message" => "SQL Error: " . $conn->error
    ]);
}

$conn->close();
?>