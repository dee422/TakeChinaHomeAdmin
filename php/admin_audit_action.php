<?php
header('Content-Type: application/json; charset=utf-8');
require_once 'db_config.php';

// 获取管理员的操作指令
$id = isset($_POST['id']) ? (int)$_POST['id'] : 0;
$newStatus = isset($_POST['status']) ? (int)$_POST['status'] : 0; 
// 2 为通过，3 为驳回

// 安全校验（需与 AdminApiService 中的默认值一致）
$adminToken = isset($_POST['admin_token']) ? $_POST['admin_token'] : '';
if ($adminToken !== "secret_admin_key") {
    die(json_encode(["success" => false, "message" => "权限验证失败"]));
}

if ($id > 0 && ($newStatus == 2 || $newStatus == 3)) {
    // 更新数据库中的状态
    $sql = "UPDATE swap_items SET status = ? WHERE id = ?";
    $stmt = $conn->prepare($sql);
    $stmt->bind_param("ii", $newStatus, $id);

    if ($stmt->execute()) {
        $msg = ($newStatus == 2) ? "准入画卷成功！" : "已驳回该申请";
        echo json_encode(["success" => true, "message" => $msg]);
    } else {
        echo json_encode(["success" => false, "message" => "数据库更新失败: " . $conn->error]);
    }
    $stmt->close();
} else {
    echo json_encode(["success" => false, "message" => "参数无效"]);
}

$conn->close();
?>