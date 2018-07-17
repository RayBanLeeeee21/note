## Abstract


current system: 
* multiple facial images
* dense correspondences across facial poses expressions and non-uniform illumination
* complex and inefficient pipelines
our method:
* do not require accurate alignment
* do not need to find dense correspondences
* works for arbitrary facial poses and expressions
* reconstruct the whole 3d facial geometry bypassing the construction and fitting of a 3d morphable model
idea:
* regression of a volumetric representation of the 3d facial geometry
* incoporate the facial landmark localization


## Introduction

characteristics of the problem:
* variant parameters
* there is a multitude of approaches to solve it.
Motivation:
* 3d face reconstruction requires 
    * complex pipelines and solving non-convex difficult otimization
    * during training and testing
perdominant approaches
1.3D morphable model(3dmm):
    * calculate dense image correspondence (it's easy to fail) during training
    * a careful initialisation is required, and solve difficult optimization problem
2.
    * solve carefully initialized non-convex problem to recover the lighting, depth and albedo(reflection rate)
3.
    * Create a neutral subject-specific 2.5D model from a near frontal image.
    * ...

method
* dataset

