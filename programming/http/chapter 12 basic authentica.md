### 12.1.2 认证协议与首部

基本认证的4个步骤
1. request :
    * method : GET
2. response : 
    * state : 401 Unauthorized
    * header :
        * WWW-Authernticate : Basic realm="Family"
3. request :
    * method : GET
    * header :
        * Authorization : Basic YnJpYW4tdG90dHk6T3ch 
            // Base-64表示, Base-64(username:password)
4. response :
    * state : 200 OK
    * hearder :
    * Authentication-Info //可选

## 12.3 基本认证的安全缺陷

基本认证的安全缺陷
* Base-64易被解码
* 重放攻击
* 不同域之间的撞库
* 假冒服务器收集用户信息