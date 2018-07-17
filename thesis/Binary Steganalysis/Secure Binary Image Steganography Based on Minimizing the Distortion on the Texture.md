

# Secure Binary Image Steganography Based on Minimizing the Distortion on the Texture


## Abstract

* 传统方法考虑视觉效果
* 本文旨在最小化嵌入扰动
* crmiLTP : complement, rotation, mirroring-invariant local texture patterns 互补-旋转-镜像不变局部纹理模式

## introduction 

### p2
* 现有方法 :
    * 追踪边缘
    * 分块嵌入

### p3
* **HVS** : Human Vision System
* 模拟图像的统计特性并最小化嵌入过程对模型的冲击
* 三种描述纹理的方法 : 
    * 基于几何
    * 基于统计特性
    * 基于模型
* crmiLTP

### p4
* 翻转扰动的评估方法 : 
    * 平滑性与连接性
    * distance reciprocal distortion-based criterion 

### p5 
* 本文方法 : 
    * 既考虑**视觉质量**, 也考虑**统计特性**
    * crmiLTP改变的加权和作为扰动
        * 权值取决于crmiLTP对扰动的敏感程度


## Flipping Distortion Measurement

### p1
* 对视觉效果的**不变性**对于纹理描述子很有必要
    * 如, 旋转不变性LBP经常用来做纹理分类
    * 