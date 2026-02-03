<?php
header('Content-Type: application/json; charset=utf-8');
require_once 'db_config.php';

$manager_id = $_GET['manager_id'] ?? 0;

try {
    // 1. 先查一下这个人的角色
    $userStmt = $pdo->prepare("SELECT role FROM admin_users WHERE id = ?");
    $userStmt->execute([$manager_id]);
    $user = $userStmt->fetch();
    $role = $user['role'] ?? 'user';

    // 2. 构建查询语句
    $query = "SELECT o.*, a.name as manager_name 
              FROM orders o 
              LEFT JOIN admin_users a ON o.manager_id = a.id";
    
    $params = [];
    
    // 逻辑：如果是普通 user (经理)，只能看自己的；如果是 admin，看全部
    if ($role !== 'admin') {
        $query .= " WHERE o.manager_id = ?";
        $params[] = $manager_id;
    }

    $query .= " ORDER BY o.created_at DESC";

    $stmt = $pdo->prepare($query);
    $stmt->execute($params);
    $orders = $stmt->fetchAll(PDO::FETCH_ASSOC);

    // 格式化数据
    foreach ($orders as &$order) {
        $order['id'] = (int)$order['id'];
        $order['manager_id'] = (int)$order['manager_id'];
        $order['is_intent'] = (int)$order['is_intent'];
        $order['details'] = json_decode($order['details'], true);
    }

    echo json_encode(["success" => true, "data" => $orders]);

} catch (Exception $e) {
    echo json_encode(["success" => false, "message" => $e->getMessage()]);
}
?>