<?php
header('Content-Type: application/json; charset=utf-8');
require_once 'db_config.php'; // 确保此文件中定义了 $conn (mysqli)

// --- 1. 路径与配置 ---
// 图片存放于上一级或更上级的 takechinahome 目录
$image_dir = "../../takechinahome/";  
// JSON 存放在 api/v1 当前目录，实现数据与图片分离
$json_path = "./gifts.json";          
$timestamp = date("YmdHis"); 

// --- 2. 获取并校验 POST 数据 ---
$name           = $_POST['name'] ?? '';
$deadline       = $_POST['deadline'] ?? '';
$spec           = $_POST['spec'] ?? '';
$desc_content   = $_POST['desc'] ?? ''; // 前端传来的描述内容
$image_list_raw = $_POST['image_list'] ?? ''; 

$image_list = json_decode($image_list_raw, true);

if (empty($name) || empty($image_list)) {
    echo json_encode(["success" => false, "message" => "数据不完整：请确保填写名称并上传图片"]);
    exit;
}

$saved_physical_files = []; // 物理路径：用于失败时删除文件
$saved_urls = [];           // URL路径：用于存入数据库

try {
    // --- 3. 保存图片文件 ---
    foreach ($image_list as $index => $base64) {
        $seq = $index + 1;
        $filename = "gift_{$timestamp}_{$seq}.jpg"; 
        $local_file = $image_dir . $filename;
        
        // base64 解码并写入文件
        if (file_put_contents($local_file, base64_decode($base64))) {
            $saved_physical_files[] = $local_file;
            $saved_urls[] = "https://ichessgeek.com/takechinahome/" . $filename;
        } else {
            throw new Exception("文件系统写入失败，请检查目录权限");
        }
    }

    // --- 4. 插入数据库记录 ---
    $images_json = json_encode($saved_urls);
    
    // 使用预处理语句，确保特殊字符（如单引号）不会破坏 SQL
    // 假设数据库表字段为：deadline, name, spec, description, images
    $stmt = $conn->prepare("INSERT INTO gifts (deadline, name, spec, description, images) VALUES (?, ?, ?, ?, ?)");
    if (!$stmt) {
        throw new Exception("SQL 预处理失败: " . $conn->error);
    }

    $stmt->bind_param("sssss", $deadline, $name, $spec, $desc_content, $images_json);
    
    if (!$stmt->execute()) {
        // 【关键】如果数据库写入失败，立即删除刚刚生成的物理图片，防止产生垃圾文件
        foreach ($saved_physical_files as $file) {
            if (file_exists($file)) @unlink($file);
        }
        throw new Exception("数据库写入失败: " . $stmt->error);
    }

    // --- 5. 重新生成静态 gifts.json ---
    // 每次上传成功后，全量抓取数据库并覆盖 JSON，确保数据同步
    // 将数据库的 description 字段 alias 为 desc 适配前端模型
    $all_res = $conn->query("SELECT id, deadline, name, spec, description as `desc`, images FROM gifts ORDER BY id DESC");
    
    $all_data = [];
    if ($all_res) {
        while($row = $all_res->fetch_assoc()) {
            // 确保 id 是整数类型（Kotlin 解析更稳健）
            $row['id'] = (int)$row['id'];
            // 将数据库存的 JSON 字符串转回数组
            $row['images'] = json_decode($row['images']);
            $all_data[] = $row;
        }
    }
    
    // 覆盖写入 api/v1/gifts.json
    file_put_contents($json_path, json_encode($all_data, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));

    echo json_encode([
        "success" => true, 
        "message" => "发布成功：资源已入库并刷新数据源",
        "debug_count" => count($all_data)
    ]);

} catch (Exception $e) {
    echo json_encode(["success" => false, "message" => $e->getMessage()]);
}

$conn->close();
?>