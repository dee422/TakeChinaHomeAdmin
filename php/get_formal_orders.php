<?php
header('Content-Type: application/json; charset=utf-8');
require_once 'db_config.php';

try {
    // 查询正式库数据
    $stmt = $pdo->prepare("SELECT * FROM formal_orders ORDER BY id DESC");
    $stmt->execute();
    $data = $stmt->fetchAll(PDO::FETCH_ASSOC);

    // 统一标记为非意向单
    foreach ($data as &$row) {
        $row['is_intent'] = 0;
    }

    // 只输出 JSON，不带任何其他文字
    echo json_encode([
        "success" => true,
        "data" => $data,
        "message" => "获取成功"
    ]);

} catch (Exception $e) {
    echo json_encode([
        "success" => false,
        "data" => [],
        "message" => "后端错误"
    ]);
}