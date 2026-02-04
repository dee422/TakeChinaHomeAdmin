<?php
header('Content-Type: application/json; charset=utf-8');
require_once 'db_config.php';

$order_id = $_POST['order_id'] ?? 0;
$gift_name = $_POST['target_gift_name'] ?? '待定';
$qty = $_POST['target_qty'] ?? 0;
$delivery_date = $_POST['delivery_date'] ?? '待定';
$contact_method = $_POST['contact_method'] ?? '待定';
$confirm_status = $_POST['intent_confirm_status'] ?? 0;

if ($order_id <= 0) {
    echo json_encode(["success" => false, "message" => "无效的订单ID"]);
    exit;
}

try {
    // 开启事务，保证数据一致性
    $pdo->beginTransaction();

    // 1. 基础更新语句
    $sql = "UPDATE orders SET 
            target_gift_name = ?, 
            target_qty = ?, 
            delivery_date = ?, 
            contact_method = ?, 
            intent_confirm_status = ?,
            ai_suggestion = NULL"; // 清空 AI 建议，因为信息已经采集完成

    $params = [$gift_name, $qty, $delivery_date, $contact_method, $confirm_status];

    // 2. 如果是“确认生成”动作 (status 为 1)
    // 额外同步更新 status 为 CONFIRMED (已确认意向)
    if ($confirm_status == 1) {
        $sql .= ", status = 'CONFIRMED'";
    }

    $sql .= " WHERE id = ?";
    $params[] = $order_id;

    $stmt = $pdo->prepare($sql);
    $stmt->execute($params);

    // 3. 提交事务
    $pdo->commit();

    // 4. 返回成功，并提示前端是否需要触发推送
    echo json_encode([
        "success" => true, 
        "message" => ($confirm_status == 1) ? "意向订单已正式生成，已通知经理" : "草案已保存",
        "is_finalized" => ($confirm_status == 1)
    ]);

} catch (PDOException $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    echo json_encode(["success" => false, "message" => "数据库错误: " . $e->getMessage()]);
}
?>