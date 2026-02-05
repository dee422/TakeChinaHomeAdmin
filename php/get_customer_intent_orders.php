<?php
// 1. 声明编码（必须在任何输出之前）
header('Content-Type: application/json; charset=utf-8');

require_once 'db_config.php';

// 获取客户端传来的 email
$email = $_GET['email'] ?? '';

if (empty($email)) {
    echo json_encode(["success" => false, "message" => "未提供用户信息"], JSON_UNESCAPED_UNICODE);
    exit;
}

try {
    // 准备 SQL
    $stmt = $pdo->prepare("SELECT 
        id, is_intent, status, contact_name, user_email,
        details, ai_suggestion, target_gift_name, target_qty,
        delivery_date, contact_method, intent_confirm_status, 
    FROM orders 
    WHERE user_email = ? 
    ORDER BY id DESC");
    
    $stmt->execute([$email]);
    $orders = $stmt->fetchAll(PDO::FETCH_ASSOC);

    // 关键：处理 details 字段
    foreach ($orders as &$order) {
        // 如果 details 为空或无效，给一个空数组防止 Kotlin 崩溃
        $decodedDetails = json_decode($order['details'], true);
        $order['details'] = $decodedDetails ?: []; 
        
        // 处理数字字段：强制转为整数，匹配 Kotlin 的 Int 类型
        $order['id'] = (int)$order['id'];
        $order['is_intent'] = (int)$order['is_intent'];
        $order['target_qty'] = (int)$order['target_qty'];
        $order['intent_confirm_status'] = (int)$order['intent_confirm_status'];
    }

    // 正确的成功输出
    echo json_encode([
        "success" => true, 
        "data" => $orders
    ], JSON_UNESCAPED_UNICODE);

} catch (PDOException $e) {
    // ❌ 注意：原来的 catch 里写错了。出错时应该返回 success: false
    echo json_encode([
        "success" => false, 
        "message" => "数据库错误: " . $e->getMessage()
    ], JSON_UNESCAPED_UNICODE);
}
?>