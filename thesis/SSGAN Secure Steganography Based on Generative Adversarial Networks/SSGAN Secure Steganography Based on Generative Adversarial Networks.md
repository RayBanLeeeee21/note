# 摘要: 
* 提出方法: 
    - 生成合适并安全的cover
    - 用GAN

# Introduction:
* [P3]:
    * 隐写算法可分为空域的与频域的
* [P6]
    * WOW
    * S-UNIWARD
    * 共同思想
        * 最小化扰动函数
        * 嵌入到多噪声和复杂纹理区域
        * 避开平滑区域
* [P7]启发: 从隐写分析的角度去考虑-->采取GAN
    * SSGAN
        * 生成网络WGAN: 生成cover, 嵌入(如HUGO)
        * 判别网络GNCNN:
    
# Secure Steganography based on GAN:
* [P2.1.2] 
    * 主要困难: GAN的损失函数的收敛与视觉质量无明显关联, 只能用肉眼评估结果并停止
        * 解决方法:WGAN: 用Wasserstein距离