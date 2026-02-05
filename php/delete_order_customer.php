<?php
header('Content-Type: application/json; charset=utf-8');
require_once 'db_config.php';

// 获取要销毁的订单 ID
$order_id = $_POST['id'] ?? 0;

if ($order_id <= 0) {
    echo json_encode(["success" => false, "message" => "无效的卷宗ID"]);
    exit;
}

try {
    // 1. 先检查订单状态，确保它是可销毁的
    $check_stmt = $pdo->prepare("SELECT is_intent, intent_confirm_status FROM orders WHERE id = ?");
    $check_stmt->execute([$order_id]);
    $order = $check_stmt->fetch(PDO::FETCH_ASSOC);

    if (!$order) {
        echo json_encode(["success" => false, "message" => "未找到该卷宗"]);
        exit;
    }

    // 2. 安全校验：如果订单已经确认锁定，或者已经不是意向单了，禁止客户删除
    if ($order['intent_confirm_status'] == 1 || $order['is_intent'] == 0) {
        echo json_encode([
            "success" => false, 
            "message" => "该卷宗已进入正式流程或已锁定，无法直接销毁，请联系您的客户经理处理。"
        ]);
        exit;
    }

    // 3. 执行物理删除（或者你可以选择执行软删除，将 status 改为 'DELETED'）
    $delete_stmt = $pdo->prepare("DELETE FROM orders WHERE id = ?");
    $result = $delete_stmt->execute([$order_id]);

    if ($result) {
        echo json_encode(["success" => true, "message" => "卷宗已销毁"]);
    } else {
        echo json_encode(["success" => false, "message" => "销毁失败，请稍后重试"]);
    }

} catch (PDOException $e) {
    echo json_encode(["success" => false, "message" => "数据库错误: " . $e->getMessage()]);
}
?>