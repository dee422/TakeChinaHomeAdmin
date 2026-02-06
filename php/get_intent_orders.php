<?php
header('Content-Type: application/json; charset=utf-8');
require_once 'db_config.php';

$manager_id = $_GET['manager_id'] ?? 0;

try {
    // 1. 获取用户信息，确认其权限
    $userStmt = $pdo->prepare("SELECT role FROM admin_users WHERE id = ?");
    $userStmt->execute([$manager_id]);
    $user = $userStmt->fetch();
    
    // 统一转为小写进行比较
    $role = isset($user['role']) ? strtolower($user['role']) : 'user';

    // 2. 构建基础 SQL (包含左连接查询经理姓名)
    $query = "SELECT o.*, a.name as manager_name 
              FROM orders o 
              LEFT JOIN admin_users a ON o.manager_id = a.id";
    
    $params = [];
    
    // 逻辑：如果是普通员工(user)，仅筛选属于自己的订单；管理员(admin)则查看全部
    if ($role !== 'admin') {
        $query .= " WHERE o.manager_id = ?";
        $params[] = $manager_id;
    }

    $query .= " ORDER BY o.created_at DESC";

    $stmt = $pdo->prepare($query);
    $stmt->execute($params);
    $orders = $stmt->fetchAll(PDO::FETCH_ASSOC);

    // 3. 数据清洗与格式化
    foreach ($orders as &$order) {
        // 强制转换数值类型，确保 Android 端 GSON 解析稳定
        $order['id'] = (int)$order['id'];
        $order['manager_id'] = (int)$order['manager_id'];
        $order['is_intent'] = (int)$order['is_intent'];
        $order['target_qty'] = (int)($order['target_qty'] ?? 0);
        $order['intent_confirm_status'] = (int)($order['intent_confirm_status']?? 0);
        $order['final_image_path'] = $order['final_image_path'] ?? ""; // 避免 null
$order['ai_suggestion'] = $order['ai_suggestion'] ?? "";

        // ✨ 核心修复：必须将 JSON 字符串解码为数组，Android 端才能识别为 List<OrderItem>
        if (!empty($order['details'])) {
            $decoded = json_decode($order['details'], true);
            // 如果解码失败（非标准 JSON），则赋予空数组
            $order['details'] = $decoded ?: [];
        } else {
            $order['details'] = [];
        }
    }

    // 4. 返回标准化的 ApiResponse 结构
    echo json_encode([
        "success" => true, 
        "data" => $orders,
        "message" => "获取成功"
    ]);

} catch (Exception $e) {
    // 捕捉异常并返回
    http_response_code(500);
    echo json_encode([
        "success" => false, 
        "message" => "服务器错误: " . $e->getMessage(),
        "data" => []
    ]);
}
?>