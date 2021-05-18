# chapter 15 协调和协定

**网络分区(*network partition*)**: 集群由于路由器的故障, 导致存在两个结点无法通信
* 特殊情况:
    * 非对称(*asymmetric*): p到q可达, q到p不可达
    * 非传递: p到q可达, q到r不可达, p到r不可达

## 15.1 共识问题和相关问题 (共识问题 & 拜占庭将军问题 & 交互一致性)

### 问题定义
共识问题:
* 定义: 
    * 每个进程$p_i$有个提议值$v_i$
    * 每个进程$p_i$最终根据提议值$v_1, v1, ..., v_n$得到一个决定值$d_i = C_i (v_1, v_2, ..., v_n)$
* 需要满足的条件:
    * 终止性: 每个进程$p_i$最后会有决定值$d_i$
    * 协定性: 如果两个进程正确(**无故障**), 则会根据相同的提议值给出相等的决定值: 
        $$ \forall~ p_i正确~\&\&~p_j正确 \rightarrow d_i = d_j = C_i(v_1, ..., v_n) = C_j(v_1, ..., v_n)$$
    * 完整性: 如果所有正确的进程都提议同一个值$v$, 则所有正确进程的决定值都满足$d_i=v$:
        $$ \{ \forall~ p_i 正确 \rightarrow v_i = v \} \rightarrow \{ \forall~ p_i 正确 \rightarrow d_i = C_i(...) = v\} $$
* 个人解读:
    * 终止性的对立面: 如果进程发生**通讯故障**或**崩溃**, 则无法达到终止性 
    * **正确**的进程即遵循**协定**的进程, 如果某个进程发生**拜占庭故障**, 则不遵循协定$f_i$, 即使知道协定也给出一个不符合协定的决定值
    * 协定函数可以是 majority / max / min 等 
        * *但不能是minority, 因为minority无法满足完整性*
<br/>


拜占庭将军问题 (*Byzantine Generals Problem*):
* 定义:
    * 司令$p_x$有一个提议值$v_x$
    * 每个进程$p_i$最终根据提议值$v_x$得到一个决定值$d_i=BG_i (v_x)$
* 需要满足的条件:
    * 终止性
    * 协议性: 如果两个进程正确 (**无故障**), 则会根据相同的提议值给出相等的决定值: 
        $$ \forall~ p_i正确~\&\&~p_j正确 \rightarrow d_i = d_j = BG_i(v_x) = BG_j(v_x)$$
    * 完整性: 如果司令$p_x$是正确的, 则所有正确进程的决定值满足$d_i=v_x$
        $$ \forall~ p_x 正确 \rightarrow \{ \forall~ p_i 正确 \rightarrow d_i = BG_i(v_x) = v_i \} $$
* 个人解读:
    * 只有 **正确** 的司令才遵循协定

交互一致性问题
* 定义: 
    * 每个进程提供一个提议值$v_i$
    * 每个进程最终根据提议值得到一个决定向量$D_i=IC_i(v_1, v_2, ..., v_n)$, 其中第$j$个向量分量表示对应于提议值$v_j$的决定值
* 需要满足的条件
    * 终止性
    * 协议性: 如果两个进程正确(**无故障**), 则会根据相同的提议值给出相同的决定向量: 
        $$ p_i正确~\&\&~p_j正确 \rightarrow D_i = D_j = IC_i(v_1, ..., v_n)= IC_j(v_1, ..., v_n)$$
    * 完整性: 如果进程$p_x$正确, 则对任意正确进程$p_x$都有$D_i[x]=v_x$
        $$ \forall~ p_x 正确 \rightarrow \{ \forall~ p_i 正确 \rightarrow D_i[x] = IC_i(...)[x] = v_x \} $$


### 三种问题的相互转化
$BG \rightarrow IC$: 
1. 假设: $BG$ 满足 
    $$ \forall~ p_i 正确 ~\&~ p_j 正确 \rightarrow BG_i(v_x) = BG_j(v_x) $$
    $$ \forall~ p_x 正确 \rightarrow  \forall~ p_i 正确 \rightarrow d_i = BG_i(v_x) = v_i $$
2. 可以对将每个进程作为司令做$BG$运算, 即 
    $$IC'_i(v_1, ..., v_n) = \{BG_i(v_1), ..., BG_i(v_n)\}$$
3. 因为每一对正确的进程$p_i$和$p_j$都会对同一个司令$p_x$作出相同的解决值, 因此
    $$ \forall~ p_i 正确 ~\&~ p_j 正确 \rightarrow IC'_i(v_1, ..., v_n)=IC'_j(v_1, ..., v_n) $$
    $$ \forall~ p_x 正确 \rightarrow  \forall~ p_i 正确 \rightarrow d_i = IC'_i(v_1, ...,  v_x)[x] = BG_i(v_x) = v_i $$
4. 因而达到了共识条件
<br/><br/>


$IC \rightarrow C$: $IC$问题中所有正确进程的决定向量都相等, 则再对决定向量加一层协定后, 结果还是一样
1. 假设$IC$满足交互一致性条件: 
    $$ \forall~ p_i 正确 ~\&~ p_j 正确 \rightarrow IC_i(v_1, ..., v_n)=IC_j(v_1, ..., v_n) $$
    $$ \forall~ p_x 正确 \rightarrow \{ \forall~ p_i 正确 \rightarrow D_i[x] = IC_i(...)[x] = v_x \} $$
2. 找一个满足共识条件的 $C$ (*例如 majority*)
    $$ \forall~ p_i正确~\&\&~p_j正确 \rightarrow d_i = d_j = D_i(v_1, ..., v_n) = D_j(v_1, ..., v_n)$$
    $$ \{ \forall~ p_i 正确 \rightarrow v_i = v \} \rightarrow \{ \forall~ p_i 正确 \rightarrow d_i = C_i(...) = v\} $$
3. 定义函数 
    $$ d'_i = C'_i(v_1, ..., v_n)=C_i(IC_i(...)[1], ..., IC_i(...)[n])=C_i(d_1, ..., d_n)$$
4. 如果所有正确进程$p_i$的提议值为$v$, 则所有正确进程的决定值$d_i$也是$v$, 即是说, 对于上一行的$C_i(...)$, 所有正确进程应用的输入都为$d_i=v$, 因此
    $$ \{ \forall~ p_i 正确 \rightarrow v_i = v \} \rightarrow \{ \forall~ p_i 正确 \rightarrow d_i = v \} \rightarrow  \{ \forall~ p_i 正确 \rightarrow d'_i = C'_i(...) = v\} $$
<br/><br/>

$C \rightarrow BG$: 好像有漏洞, 推理不出来
1. 假设: $C$ 满足
    $$ \forall~ p_i正确~\&\&~p_j正确 \rightarrow d_i = d_j = C_i(v_1, ..., v_n) = C_j(v_1, ..., v_n)$$
    $$ \{ \forall~ p_i 正确 \rightarrow v_i = v \} \rightarrow \{ \forall~ p_i 正确 \rightarrow d_i = C_i(...) = v\} $$
2. 如果除司令$p_x$以及两个进程$p_i$与$p_j$以后, 其它进程是否正确都是固定的, 则可以$C$把看作与司令有关的一个函数$BG'(x)$. 其它进程的正确与否情况很多, 但都一定满足:
    $$ \forall~ p_i正确~\&\&~p_j正确 \rightarrow d_i = d_j = BG'_i(v_x) = BG'_j(v_x)$$
3. 如果司令与其它正确的进程都提议$v$, 则由共识条件的完整性可得, 所有正确进程的决定值都为$v$, 与司令一致
4. 如果并非所有正确进程的提议值都一致, 则正确进程的决定值相等, **但不一定与司令的提议值一致**.
<br/>


为什么N=6, f=2时不能保证正确:
* 假设司令x和将军p为叛徒, 将军{a, b, c, d}不是叛徒
    * 第1轮: 司令组播: 司令x向a, b说进攻, 跟c, d说撤退
    * 第2轮: 同级级播: p向a, b说进攻, 跟c, d说撤退, 则
        * a共收到{x, p, b}三个进攻, b共收到{x, p, a}三个进攻, 因此{a, b}决定进攻
        * c共收到{x, p, d}三个撤退, d共收到{x, p, c}三个撤退, 因此{c, d}决定撤退
    * 结果: a, b 战败

如果N=7, f=2:
* 假设司令x和将军p为叛徒, 将军{a, b, c, d, e}不是叛徒
    * 情况1:
        * 第1轮: 司令组播: 司令x向a, b, c说进攻, 跟d, e说撤退
        * 第2轮: 同级级播: p向a, b, c说进攻, 跟d, e说撤退
            * a共收到{x, p, b, c}四个进攻, b共收到{x, p, a, c}四个进攻, c收到{x, p, a, b}四个进攻
            * d共收到{x, p, e}三个撤退, e共收到{x, p, d}三个撤退, 因此{c, d}决定撤退
// TODO