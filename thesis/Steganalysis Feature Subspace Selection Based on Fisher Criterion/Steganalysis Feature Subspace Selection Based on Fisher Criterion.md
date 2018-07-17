## Abstract

*[proposed]* **Conditional adversarial networks**
1. Not only learn the **mapping function**
2. But also learn the **loss function**

* It's possible to apply the sam generic approach to problems which **loss function is complex**
* Effective at 
    * synthesizing picture from label maps
    * reconstructing objects from edge maps
    * coloring images
* We no longer hand-engineer **loss-function** as well as **mapping function**

## Introduction

[par 1]
image-to-image translation: translate the image of a representation to another
* many-to-one problem(computer vision): mapping photographs to edges, segments, or semantic labels
* one-to-many problem(computer graphic): mapping edges, segments, or semantic labels to realistic image
* Among these problem, the setting is the same: **pix2pix**

*[goal]* develop a common framework for all these problems.

[par 2]
*[problem]* **CNN** learn to minimize a loss function
* we still tell the CNN what we wish it to minimize.
* Using **Euclidean distance** directly may will cause blurry output
    * Euclidean distance is minimized by averaging all plausible outputs
* **Thinking of a loss function** which can make CNN output what we want is an open problem.

[par 3]
**GAN** can learn a **loss** which
* tries to classify if the output image is "real" or "fake"
* depends on the data
* appropriate for satisfying the goal

[par 4,5]

## Related work
