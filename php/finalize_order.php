<?php
header('Content-Type: application/json; charset=utf-8');
require_once 'db_config.php';

$order_id = $_POST['order_id'] ?? 0;
$local_path = $_POST['local_path'] ?? ''; // 本地存储路径
$manager_email = $_POST['manager_email'] ?? '未知';

if ($order_id <= 0) {
    echo json_encode(["success" => false, "message" => "无效订单ID"]);
    exit;
}

try {
    $pdo->beginTransaction();

    // 1. 将数据迁移到正式表 (假设 formal_orders 表结构与 orders 类似)
    // 我们同时更新本地存储路径 local_image_path
    $sql_move = "INSERT INTO formal_orders (
                    original_order_id, target_gift_name, target_qty, 
                    delivery_date, contact_method, contact_name, 
                    manager_email, final_image_path, local_image_path, created_at
                ) 
                SELECT id, target_gift_name, target_qty, 
                       delivery_date, contact_method, contact_name, 
                       ?, final_image_path, ?, NOW() 
                FROM orders WHERE id = ?";
    
    $stmt_move = $pdo->prepare($sql_move);
    $stmt_move->execute([$manager_email, $local_path, $order_id]);

    // 2. 插入站内通知消息 (存入 order_notifications 或 messages 表)
    // 逻辑：向该订单关联的账号发送通知
    $notice_text = "【正式卷宗已生成】订单已由经理($manager_email)确认并转入正式库。";
    $sql_notice = "INSERT INTO order_notifications (order_id, message, type, created_at) 
                   VALUES (?, ?, 'FORMAL_TRANSFERRED', NOW())";
    $stmt_notice = $pdo->prepare($sql_notice);
    $stmt_notice->execute([$order_id, $notice_text]);

    // 3. 从原有的意向订单表中删除
    $sql_del = "DELETE FROM orders WHERE id = ?";
    $stmt_del = $pdo->prepare($sql_del);
    $stmt_del->execute([$order_id]);

    $pdo->commit();
    echo json_encode(["success" => true, "message" => "转正成功"]);

} catch (Exception $e) {
    $pdo->rollBack();
    echo json_encode(["success" => false, "message" => "事务失败: " . $e->getMessage()]);
}
?>