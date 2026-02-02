<?php
header('Content-Type: application/json; charset=utf-8');

// 1. 获取请求参数
$provider = $_POST['provider'] ?? ''; // zhipu, openai
$apiKey   = $_POST['api_key'] ?? '';
$prompt   = $_POST['prompt'] ?? '';
$noChinese = $_POST['no_chinese'] ?? 'false';

if (empty($apiKey) || empty($prompt)) {
    echo json_encode(['success' => false, 'message' => '缺少必要参数']);
    exit;
}

// 2. 处理“禁止中文”逻辑
if ($noChinese === 'true') {
    $prompt .= " (IMPORTANT: Do not include any text, letters, or Chinese characters in the image. Focus on pure artistic visual elements only.)";
}

// 3. 根据引擎分发请求
switch ($provider) {
    case 'zhipu':
        $result = callZhipuCogView($apiKey, $prompt);
        break;
    case 'openai':
        $result = callOpenAIDalle($apiKey, $prompt);
        break;
    default:
        $result = ['success' => false, 'message' => '暂不支持该生图引擎'];
}

echo json_encode($result);

/**
 * 智谱 CogView 调用
 */
function callZhipuCogView($apiKey, $prompt) {
    $url = "https://open.bigmodel.cn/api/paas/v4/images/generations";
    
    // 智谱使用标准 API Key 认证
    $headers = [
        "Authorization: Bearer $apiKey",
        "Content-Type: application/json"
    ];

    $data = [
        "model" => "cogview-3-plus", // 或最新版 cogview-4
        "prompt" => $prompt,
        "size" => "1024x1024"
    ];

    return postRequest($url, $headers, $data, function($res) {
        // 智谱返回格式解析
        if (isset($res['data'][0]['url'])) {
            return ['success' => true, 'image_url' => $res['data'][0]['url']];
        }
        return ['success' => false, 'message' => $res['error']['message'] ?? '生成失败'];
    });
}

/**
 * OpenAI DALL-E 3 调用
 */
function callOpenAIDalle($apiKey, $prompt) {
    $url = "https://api.openai.com/v1/images/generations";
    $headers = [
        "Authorization: Bearer $apiKey",
        "Content-Type: application/json"
    ];

    $data = [
        "model" => "dall-e-3",
        "prompt" => $prompt,
        "n" => 1,
        "size" => "1024x1024"
    ];

    return postRequest($url, $headers, $data, function($res) {
        if (isset($res['data'][0]['url'])) {
            return ['success' => true, 'image_url' => $res['data'][0]['url']];
        }
        return ['success' => false, 'message' => $res['error']['message'] ?? 'DALL-E 生成失败'];
    });
}

/**
 * 通用 CURL 请求函数
 */
function postRequest($url, $headers, $data, $parser) {
    $ch = curl_init($url);
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_POST, true);
    curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($data));
    curl_setopt($ch, CURLOPT_HTTPHEADER, $headers);
    // 选填：如果你的服务器 SSL 证书有问题，可以临时关闭（正式环境建议开启）
    curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false); 

    $response = curl_exec($ch);
    $error = curl_error($ch);
    curl_close($ch);

    if ($error) return ['success' => false, 'message' => "CURL Error: $error"];

    $decoded = json_decode($response, true);
    return $parser($decoded);
}