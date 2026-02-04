<?php
header('Content-Type: application/json; charset=utf-8');
require_once 'db_config.php'; 

// --- 配置区 ---
$api_key = "084d1416f3de4433ab112b689b226521.golSWYxybbkLszhv"; 
$api_url = "https://open.bigmodel.cn/api/paas/v4/chat/completions";

// 1. 获取参数
$order_id = isset($_GET['order_id']) ? intval($_GET['order_id']) : 0;

if ($order_id <= 0) {
    echo json_encode(["success" => false, "message" => "无效的卷宗ID"]);
    exit;
}

try {
    // 2. 升级查询语句（补上了 ai_suggestion 字段）
    $stmt = $pdo->prepare("SELECT details, ai_suggestion, target_gift_name, target_qty, delivery_date, contact_method FROM orders WHERE id = ?");
    $stmt->execute([$order_id]);
    $order = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$order) {
        echo json_encode(["success" => false, "message" => "未找到该卷宗"]);
        exit;
    }

    // 解析 details 获取初始意向
    $details = json_decode($order['details'], true);
    $initial_gift = isset($details[0]['name']) ? $details[0]['name'] : '未选择';
    $initial_qty = isset($details[0]['qty']) ? $details[0]['qty'] : 0;

    // ✨ 检查缓存：如果已经有建议了，直接返回
    if (!empty($order['ai_suggestion'])) {
        echo json_encode(["success" => true, "data" => $order['ai_suggestion']]);
        exit;
    }

    // 3. 构造 AI 提示词 (Prompt)
    $prompt = "你是一位高端礼品定制中心的管家。
    客户初始卷宗显示：拟选【{$initial_gift}】，数量【{$initial_qty}】。
    
    目前系统登记的意向核对进度：
    - 确认礼品名称：{$order['target_gift_name']} (当前显示为'待定'则需确认)
    - 确认具体数量：{$order['target_qty']} (当前为0则需确认)
    - 期望交货日期：{$order['delivery_date']}
    - 联系方式及时间：{$order['contact_method']}
    
    任务指令：
    1. 第一优先级：若礼品名和数量字段仍为'待定'或'0'，请引用初始卷宗的信息（{$initial_gift} x {$initial_qty}）向客户核实是否以此为准。
    2. 第二优先级：引导客户提供缺失的交货时间和联系方式。
    3. 边界守则：若客户询问超出这四个基础信息（如价格折扣、材质细节），请客气告知：'这部分专业细节，稍后客户经理与您联系时将为您深度解答'。
    4. 语气要求：温润如玉、专业得体、拒绝啰嗦。
    5. 长度限制：一行文字，80字以内。";

    $post_data = [
        "model" => "glm-4",
        "messages" => [
            ["role" => "user", "content" => $prompt]
        ]
    ];

    $ch = curl_init($api_url);
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_POST, true);
    curl_setopt($ch, CURLOPT_HTTPHEADER, [
        "Content-Type: application/json",
        "Authorization: Bearer $api_key"
    ]);
    curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($post_data));

    $response = curl_exec($ch);
    $res_json = json_decode($response, true);
    curl_close($ch);

    if (isset($res_json['choices'][0]['message']['content'])) {
        $ai_result = $res_json['choices'][0]['message']['content'];
        
        // 存入数据库
        $update = $pdo->prepare("UPDATE orders SET ai_suggestion = ? WHERE id = ?");
        $update->execute([$ai_result, $order_id]);

        echo json_encode(["success" => true, "data" => $ai_result]);
    } else {
        echo json_encode(["success" => false, "message" => "AI 响应异常", "debug" => $res_json]);
    }

} catch (Exception $e) {
    echo json_encode([
        "success" => false, 
        "message" => "服务暂不可用: " . $e->getMessage()
    ]);
}