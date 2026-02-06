<?php
/**
 * 位置：/api/v1/complete_order_intent.php
 * 功能：将意向订单转为正式订单（上传照片并结案）
 */
header('Content-Type: application/json; charset=utf-8');
require_once 'db_config.php';

$order_id = $_POST['order_id'] ?? 0;
$manager_id = $_POST['manager_id'] ?? 0;

if ($order_id <= 0) {
    echo json_encode(["success" => false, "message" => "无效订单ID"]);
    exit;
}

$uploaded_path = "";
// 处理上传的正式卷宗照片
if (isset($_FILES['formal_image']) && $_FILES['formal_image']['error'] === UPLOAD_ERR_OK) {
    $upload_dir = 'formal_orders/';
    if (!is_dir($upload_dir)) mkdir($upload_dir, 0777, true);
    
    $file_ext = pathinfo($_FILES['formal_image']['name'], PATHINFO_EXTENSION);
    $file_name = "formal_" . $order_id . "_" . time() . "." . $file_ext;
    $target_file = $upload_dir . $file_name;

    if (move_uploaded_file($_FILES['formal_image']['tmp_name'], $target_file)) {
        $uploaded_path = "https://ichessgeek.com/api/v1/" . $target_file;
    }
}

try {
    // ✨ 核心转变：is_intent 变 0，状态变完成
    $sql = "UPDATE orders SET 
            is_intent = 0, 
            status = 'COMPLETED', 
            final_image_path = ? 
            WHERE id = ? AND manager_id = ?";
    
    $stmt = $pdo->prepare($sql);
    $stmt->execute([$uploaded_path, $order_id, $manager_id]);

    echo json_encode(["success" => true, "message" => "卷宗已转正并结案"]);
} catch (PDOException $e) {
    echo json_encode(["success" => false, "message" => $e->getMessage()]);
}
?>