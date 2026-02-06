<?php
/**
 * 位置：/api/v1/delete_order_manager.php
 * 功能：管理端终止并销毁意向订单
 */
header('Content-Type: application/json; charset=utf-8');
require_once 'db_config.php';

$order_id = $_POST['id'] ?? 0;
$manager_id = $_POST['manager_id'] ?? 0;

if ($order_id <= 0) {
    echo json_encode(["success" => false, "message" => "无效订单ID"]);
    exit;
}

try {
    // 只有对应的经理或管理员才能删除
    $sql = "DELETE FROM orders WHERE id = ? AND (manager_id = ? OR manager_id = 0)";
    $stmt = $pdo->prepare($sql);
    $stmt->execute([$order_id, $manager_id]);

    if ($stmt->rowCount() > 0) {
        echo json_encode(["success" => true, "message" => "意向卷宗已销毁"]);
    } else {
        echo json_encode(["success" => false, "message" => "删除失败，可能无权操作"]);
    }
} catch (PDOException $e) {
    echo json_encode(["success" => false, "message" => "数据库错误: " . $e->getMessage()]);
}
?>