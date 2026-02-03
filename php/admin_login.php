<?php
header('Content-Type: application/json');
require_once 'db_config.php';

$email = $_POST['email'] ?? '';
$password = $_POST['password'] ?? '';

if (empty($email) || empty($password)) {
    echo json_encode(["success" => false, "message" => "参数缺失"]);
    exit;
}

try {
    // 实际项目中请使用 password_verify
    $stmt = $pdo->prepare("SELECT email, name, role FROM admin_users WHERE email = ? AND password = ?");
    $stmt->execute([$email, $password]);
    $user = $stmt->fetch(PDO::FETCH_ASSOC);

    if ($user) {
        echo json_encode([
            "success" => true,
            "data" => [
                "email" => $user['email'],
                "name" => $user['name'],
                "role" => $user['role'],
                "token" => bin2hex(random_bytes(16)) // 生成一个临时Token
            ],
            "message" => "登录成功"
        ]);
    } else {
        echo json_encode(["success" => false, "message" => "邮箱或密码错误"]);
    }
} catch (PDOException $e) {
    echo json_encode(["success" => false, "message" => "数据库错误"]);
}
?>