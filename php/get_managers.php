<?php
header('Content-Type: application/json; charset=utf-8');
require_once 'db_config.php';

try {
    // ✨ 关键：确保字段名包含 username 和 nickname
    // 如果你的表中只有 'name'，请写成 SELECT name as nickname...
    $stmt = $pdo->query("SELECT name as nickname FROM admin_users WHERE role = 'user' OR role = 'admin'");
    $managers = $stmt->fetchAll(PDO::FETCH_ASSOC);

    echo json_encode(["success" => true, "data" => $managers], JSON_UNESCAPED_UNICODE);

} catch (PDOException $e) {
    echo json_encode(["success" => false, "message" => $e->getMessage()]);
}
?>