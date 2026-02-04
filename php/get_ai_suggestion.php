<?php
header('Content-Type: application/json; charset=utf-8');
require_once 'db_config.php'; // 确保你的 db_config 使用的是 PDO 连接

// --- 配置区 ---
$api_key = "sk-041ed22c0d1148afa289808e1795a94c"; // ✨ 请在此处填入你的 DeepSeek API Key
$api_url = "https://api.deepseek.com/chat/completions";

// 1. 获取参数
$order_id = isset($_GET['order_id']) ? intval($_GET['order_id']) : 0;

if ($order_id <= 0) {
    echo json_encode(["success" => false, "message" => "无效的卷宗ID"]);
    exit;
}

try {
    // 2. 获取订单详情（关联查询订单详情，让 AI 知道客户买了什么）
    $stmt = $pdo->prepare("SELECT contact_name, ai_suggestion FROM orders WHERE id = ?");
    $stmt->execute([$order_id]);
    $order = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$order) {
        echo json_encode(["success" => false, "message" => "未找到该卷宗"]);
        exit;
    }

    // 如果数据库已经有缓存的建议了，直接返回，省下 API 费用
    if (!empty($order['ai_suggestion'])) {
        echo json_encode(["success" => true, "data" => $order['ai_suggestion']]);
        exit;
    }

    // 3. 准备 AI 提示词 (Prompt)
    $customer_name = $order['contact_name'];
    
    // 进阶：你可以通过 SQL 查询把订单里的具体礼品名字也查出来，塞进 Prompt
    $prompt = "你是一位资深的礼品定制专家和销售教练。
    现有客户：{$customer_name}。
    任务：请为销售经理提供一段专业且温情的沟通话术建议。
    要求：
    1. 风格要优雅、专业，体现中国传统礼赠文化。
    2. 字数在 80 字以内。
    3. 侧重于如何引导客户从‘意向’转为‘正式下单’。";

    // 4. 使用 cURL 调用 DeepSeek API
    $post_data = [
        "model" => "deepseek-chat", // 或者是 "deepseek-reasoner"
        "messages" => [
            ["role" => "system", "content" => "你是一个古玩与高端礼品行业的专业销售助手。"],
            ["role" => "user", "content" => $prompt]
        ],
        "temperature" => 0.7,
        "max_tokens" => 200
    ];

    $ch = curl_init($api_url);
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_POST, true);
    curl_setopt($ch, CURLOPT_HTTPHEADER, [
        "Content-Type: application/json",
        "Authorization: Bearer " . $api_key
    ]);
    curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($post_data));
    
    // 执行请求
    $response = curl_exec($ch);
    $http_code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    
    if (curl_errno($ch)) {
        throw new Exception('CURL 错误: ' . curl_error($ch));
    }
    curl_close($ch);

    // 5. 解析 AI 返回结果
    $res_data = json_decode($response, true);
    
    if ($http_code === 200 && isset($res_data['choices'][0]['message']['content'])) {
        $ai_result = trim($res_data['choices'][0]['message']['content']);
        
        // 6. 将 AI 结果持久化到数据库
        $update = $pdo->prepare("UPDATE orders SET ai_suggestion = ? WHERE id = ?");
        $update->execute([$ai_result, $order_id]);

        echo json_encode([
            "success" => true,
            "data" => $ai_result,
            "message" => "AI 深度分析完成"
        ]);
    } else {
        // 如果 API 报错，给出具体原因
        $error_msg = isset($res_data['error']['message']) ? $res_data['error']['message'] : "AI 响应异常";
        throw new Exception("API 错误 ($http_code): " . $error_msg);
    }

} catch (Exception $e) {
    echo json_encode([
        "success" => false, 
        "message" => "服务暂不可用: " . $e->getMessage(),
        "data" => "暂时无法获取 AI 建议，请尝试手动沟通。"
    ]);
}