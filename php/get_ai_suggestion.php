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
    $stmt = $pdo->prepare("SELECT contact_name, ai_suggestion, target_gift_name, target_qty, delivery_date, contact_method FROM orders WHERE id = ?");
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
    $current_status = "
    - 意向礼品：{$order['target_gift_name']}
    - 意向数量：{$order['target_qty']}
    - 交货时间：{$order['delivery_date']}
    - 联系方式：{$order['contact_method']}
    ";
    
    // 进阶：你可以通过 SQL 查询把订单里的具体礼品名字也查出来，塞进 Prompt
    $prompt = "你是一位资深的商务经理。请针对以下客户意向进度生成一段沟通话术。
    客户姓名：{$order['contact_name']}
    当前采集进度：{$current_status}
    
    任务指令：
    1. 检查上述进度，如果字段显示为'待定'或'0'，请在话术中委婉、礼貌地引导客户提供该信息。
    2. 如果所有信息都已明确，请生成一段确认总结，并告知将生成正式意向卷宗。
    3. 语气要专业且温润，字数控制在100字以内。
    4. 结尾要让客户感受到流程的严谨和服务的贴心。";

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