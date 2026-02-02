<?php
header('Content-Type: application/json; charset=utf-8');
require_once 'db_config.php';

$json_path = "./gifts.json";

// 获取 POST 数据
$id           = $_POST['id'] ?? '';
$name         = $_POST['name'] ?? '';
$deadline     = $_POST['deadline'] ?? '';
$spec         = $_POST['spec'] ?? '';
$desc_content = $_POST['desc'] ?? ''; // 对应数据库的 description

if (empty($id) || empty($name)) {
    echo json_encode(["success" => false, "message" => "修改失败：ID 和名称不能为空"]);
    exit;
}

try {
    // 1. 更新数据库
    $stmt = $conn->prepare("UPDATE gifts SET name=?, deadline=?, spec=?, description=? WHERE id=?");
    $stmt->bind_param("ssssi", $name, $deadline, $spec, $desc_content, $id);
    
    if ($stmt->execute()) {
        // 2. 重新生成 gifts.json 保持同步
        $all_res = $conn->query("SELECT id, deadline, name, spec, description as `desc`, images FROM gifts ORDER BY id DESC");
        $all_data = [];
        while($row = $all_res->fetch_assoc()) {
            $row['id'] = (int)$row['id'];
            $row['images'] = json_decode($row['images']);
            $all_data[] = $row;
        }
        file_put_contents($json_path, json_encode($all_data, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
        
        echo json_encode(["success" => true, "message" => "修改成功"]);
    } else {
        throw new Exception("更新失败: " . $stmt->error);
    }
} catch (Exception $e) {
    echo json_encode(["success" => false, "message" => $e->getMessage()]);
}
$conn->close();
?>