<?php
header('Content-Type: application/json');
require_once 'db_config.php';

try {
    // 获取所有用户，按创建时间降序排列
    $stmt = $pdo->query("SELECT email, name, role, created_at as createdAt FROM admin_users ORDER BY created_at DESC");
    $users = $stmt->fetchAll(PDO::FETCH_ASSOC);

    // 包装成 Android 端定义的 ApiResponse 格式
    echo json_encode([
        "success" => true,
        "data" => $users,
        "message" => "获取成功"
    ]);
} catch (PDOException $e) {
    echo json_encode([
        "success" => false,
        "data" => null,
        "message" => "查询失败: " . $e->getMessage()
    ]);
}
?>