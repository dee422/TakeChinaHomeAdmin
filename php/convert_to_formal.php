<?php
// convert_to_formal.php
header('Content-Type: application/json; charset=utf-8');
require_once 'db_config.php';

// 获取管理端传来的数据
$orderId = $_POST['order_id'] ?? '';
$managerName = $_POST['manager_name'] ?? '系统管理员';

if (empty($orderId)) {
    echo json_encode(["success" => false, "message" => "缺少订单编号"]);
    exit;
}

try {
    // 1. 先查出当前的意向数据，确保它是已锁定状态 (intent_confirm_status = 1)
    $stmt = $pdo->prepare("SELECT * FROM orders WHERE id = ?");
    $stmt->execute([$orderId]);
    $order = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$order) {
        echo json_encode(["success" => false, "message" => "未找到该卷宗"]);
        exit;
    }

    if ($order['intent_confirm_status'] != 1) {
        echo json_encode(["success" => false, "message" => "客户尚未锁定意向，无法转为正式订单"]);
        exit;
    }

    // 2. 更新逻辑：
    // - is_intent 改为 0 (正式订单)
    // - status 改为 '已确认' 或 '正式订单'
    // - 将 target_ 数据同步，防止后续被意外篡改
    $updateStmt = $pdo->prepare("UPDATE orders SET 
        is_intent = 0, 
        status = '正式订单',
        manager_name = ?,
        update_time = NOW() 
        WHERE id = ?");
    
    $result = $updateStmt->execute([$managerName, $orderId]);

    if ($result) {
        echo json_encode([
            "success" => true, 
            "message" => "卷宗已正式入库",
            "data" => ["order_id" => $orderId]
        ]);
    } else {
        echo json_encode(["success" => false, "message" => "数据库更新失败"]);
    }

} catch (PDOException $e) {
    echo json_encode(["success" => false, "message" => $e->getMessage()]);
}
?>