<?php
header('Content-Type: application/json; charset=utf-8');
require_once 'db_config.php';

$id = $_POST['id'] ?? '';

if (empty($id)) {
    echo json_encode(["success" => false, "message" => "删除失败：缺少产品 ID"]);
    exit;
}

try {
    // 1. (可选) 如果需要删除服务器上的物理图片，先在此查询图片路径并 unlink
    
    // 2. 从数据库删除记录
    $stmt = $conn->prepare("DELETE FROM gifts WHERE id = ?");
    $stmt->bind_param("i", $id);

    if ($stmt->execute()) {
        // 3. 核心：同步更新 gifts.json
        $res = $conn->query("SELECT id, deadline, name, spec, description as `desc`, images FROM gifts ORDER BY id DESC");
        $all_data = [];
        while($row = $res->fetch_assoc()) {
            $row['id'] = (int)$row['id'];
            $row['images'] = json_decode($row['images']);
            $all_data[] = $row;
        }
        file_put_contents("../gifts.json", json_encode($all_data, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
        
        echo json_encode(["success" => true, "message" => "产品已永久下架"]);
    } else {
        throw new Exception("数据库删除操作失败");
    }
} catch (Exception $e) {
    echo json_encode(["success" => false, "message" => $e->getMessage()]);
}

$conn->close();
?>