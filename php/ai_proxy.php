<?php
header('Content-Type: application/json; charset=utf-8');

// 1. 获取 Android 端传来的数据
$provider = $_POST['provider'] ?? '';
$apiKey   = $_POST['api_key'] ?? '';
$text     = $_POST['text'] ?? '';
$samples  = $_POST['samples'] ?? '';

// 2. 基础检查
if (empty($apiKey) || empty($text)) {
    echo json_encode(["success" => false, "message" => "缺少 API Key 或待描述文本"]);
    exit;
}

// 3. 配置不同引擎的请求参数
$configs = [
    "zhipu" => [
        "url" => "https://open.bigmodel.cn/api/paas/v4/chat/completions",
        "model" => "glm-4-flash"
    ],
    "deepseek" => [
        "url" => "https://api.deepseek.com/chat/completions",
        "model" => "deepseek-chat"
    ],
    "qwen" => [
        "url" => "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation",
        "model" => "qwen-plus"
    ],
    "gemini" => [
        "url" => "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent",
        "model" => "gemini-1.5-flash"
    ]
];

if (!isset($configs[$provider])) {
    echo json_encode(["success" => false, "message" => "不支持的 AI 引擎: $provider"]);
    exit;
}

$currentConfig = $configs[$provider];

// 4. 构建 Prompt (提示词策略)
$systemPrompt = "你是一位专业的古玩鉴赏家和文案大师。请参考用户提供的【优质样本】风格，对【原始描述】进行润色。要求：语言典雅、专业、富有吸引力，只需返回润色后的正文，不要有任何解释性文字。";
$userContent = "【优质样本】：\n$samples\n\n【原始描述】：\n$text";

// 5. 适配请求体 (针对 OpenAI 兼容格式)
$postData = [
    "model" => $currentConfig['model'],
    "messages" => [
        ["role" => "system", "content" => $systemPrompt],
        ["role" => "user", "content" => $userContent]
    ],
    "temperature" => 0.7
];

// 特殊处理：Gemini 的数据结构与 OpenAI 不同
if ($provider === "gemini") {
    $postData = [
        "contents" => [
            ["role" => "user", "parts" => [["text" => $systemPrompt . "\n\n" . $userContent]]]
        ]
    ];
    $url = $currentConfig['url'] . "?key=" . $apiKey;
} else {
    $url = $currentConfig['url'];
}

// 6. 发起 CURL 请求
$ch = curl_init($url);
$headers = ["Content-Type: application/json"];
if ($provider !== "gemini") {
    $headers[] = "Authorization: Bearer " . $apiKey;
}

curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
curl_setopt($ch, CURLOPT_POST, true);
curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($postData));
curl_setopt($ch, CURLOPT_HTTPHEADER, $headers);
// 建议设置超时，防止 AI 响应过慢导致 PHP 进程挂起
curl_setopt($ch, CURLOPT_TIMEOUT, 30); 

$response = curl_exec($ch);
$curlError = curl_error($ch);
$httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
curl_close($ch);

if ($curlError) {
    echo json_encode(["success" => false, "message" => "网络请求错误: $curlError"]);
    exit;
}

$result = json_decode($response, true);

// 7. 解析不同平台的返回结果
$refinedText = "";
if ($provider === "gemini") {
    $refinedText = $result['candidates'][0]['content']['parts'][0]['text'] ?? "";
} else {
    // 适配 DeepSeek, 智谱, 通义千问等 OpenAI 格式
    $refinedText = $result['choices'][0]['message']['content'] ?? "";
}

if (!empty($refinedText)) {
    echo json_encode([
        "success" => true,
        "refined_text" => trim($refinedText)
    ]);
} else {
    echo json_encode([
        "success" => false, 
        "message" => "AI 响应解析失败", 
        "debug_info" => $result,
        "http_code" => $httpCode
    ]);
}
?>