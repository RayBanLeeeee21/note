<style>
body{background-color:#221a0f}    
body{background-color:#362712; font-size:15px; width:50em} 
h1{color:#ddd}
h2{color:#ddd}
h3{color:#ddd}
h4{color:#ddd}
strong{ color:#d64}
em{ color:#ea0}
li{ color:#ea6; }
li li{ color:#bb7 }
li li li{ color:#7aa }
li li li li{ color:#aa8 }
li li li li li{ color:#8b6 }
li li li li li li{ color:#8b6 }
</style>


##Abstract

**[problem]**: place a **full size** color image within another image of the **same size**.

### contribution:
* **[method]**:
    * *Deep neural networks are simultaneously trained to create the **hiding process** and **revealing process**
        * which are designed to specifically work as a pair
    * Database(sample):
        * *?The system is trained on images **drawn randomly from the ImageNet database**
    * characteristic:
        * *our approach compresses and distributes the secret image's representation **across all of the available bits**
        * work well on natural images
* carefully examine how the result is achieved and expore extensions

## Introdution
* steganography: 
    * The steganographic process places a hidden message in a transport medium, called **carrier**, which may by publicly visible.
        * the hidden message can be encrypted, to 
            * increasing the perceived randomness and
            * decreasing the likelihood of content discovery even if the existence of the message detected. 

* application:
    * Illegal: used by crimes to planning and coordinating crimainal activities.
    * Legal: embed authorship information


* **[problem]**:
    * difficulty:
        * embedding a message can alter the [appearance] and [underlying statistics of the carrier], the amount of alteration depends on
            * the amount of information to be hidden. measured by [bits-per-pixel(bpp)]. 
                * often < 0.4 bpp
            * the carrier image itself
                * hidding on noisy, high-frequency filled regions of an image yields less humanly detectable perturbations       

* **[current method]**:
    * LSB: the most common steganography approachese manipualate the **least significant bits(LSB)**
        * uniformly
        * adaptively
    * statistical analysis can reveal if an image hides messages

    * Advanced methods attempt to preserve the image statistic by creating and matching models of the **1st or 2nd order statistics** of the set of possible cover images **explicitly**
        * e.g. HUGO

* **[method]**
    * use neural networks to **implicitly** model the **distribution of natural images**
    * to embed a larger message--a full size image.
        * A encoder network is trained to determines where to place the information(dispersed throughout the bits in the image)
        * A decoder network is trained to reveal the secret image
    * ?The networks are trained only once and are independent of the cover and secret images


    * characteristic:
        * incorporate neural networks into the [hidding process itself]
        * i.e., 
            * <->comparing with other methods:
                * some methods use DNN to select which LSBs to replace in
                * others use DNNs to determine which bits to extract from the container images

    * **[goal]**
        * hide a full N\*N\*RGB pixel secret image in another N\*N\*RGB cover image,
            * with minimal distortion to the cover
        * find trade-off in the [quality of the carrier] and [secret image]
            * <->comparing to hidding text message, the secret image is lossy
        * do not expect that it would not be discoveried by statistical analysis
            * but commonly used method cannot find
            * and visually hard to detect
        * trade-off the [difficulty of existence-discovery] with [reconstruction quality]

## Architectures and Error Propagation

* The closest analogue of our work is image compression through auto-encoding networks
    
### method
* network
    * the system learn to compress the information from the secret image into the least noticable portions 

    * function
        * for prep network, hiding Network: L(c,c') = ||c - c'||2  
        * for all: L(c,c',s,s') = ||c - c'||2 + beta||s - s'||2 


    * three components
        * prep network 
            * output: 3 channels
            * increase the size of smaller secret image to be as large as cover
            * transform the color-based pixels to more useful features, e.g., edges
        * hiding network
            * input: 
                * N*N depth concatenated RGB channels of cover, and 
                * the output of prep network 
            * output: [container image]
        * reveal network 
            * input: container image (without cover or secret image)
            * removes the cover image to reveal the secret image


    1. the error and weight ensure that the representations formed early in the preparation network 
        * as well as those used for reconstruction of cover image alse encode information about the secret image
    2. To ensure thta the networks do not simply encode the secret image in the LSBs, 
        * a small amount of noise, which may flips some LSBs, is added to the output of the hiding network while training
        * which ensure the LSB was not the sole container of the secret image's reconstruction


## Empirical Evaluation

* method: The networks were trained using randomly selected pairs of images from the ImageNet training set(natural images)

* figure 4
    measured by MSE
    1. the bigger beta is, the higer cover MSE is, and the lower secret MSE is

* figure 5

* <->comparing to LSB substitution
    * LSB substitution: cover MSE: 5.4, secret MSE: >4.0

    * why is the error large?
        * the distribution of LSBs and MSBs of natural images are different
            * therefore, even when the distribution of cover and secret are similar, 
            * replace LSBs of cover with secret MSBs will cause large errors than using the average of LSBs
        * These error rates are higher than the error of our system

* The limitation see figure 6

* What if the attacker get the original cover?
    * minimizes the pixel-wise correlation between the [residual of the cover image] and the [secret image]: corr(Rc, S)
        * (the paper didn't tell the correlation function)


## Where is the secret image encoded
* problem:
    * more than half of the information is part of a hidden message
    * but we can make it more difficult to be discovered

* first we must determine where the information of the secret image resides

* StegExpose cannot find the information of the secret image form the LSBs


Problem:
Place a full size color image within another image of the same size.

Difficulty:
Embedding a message can alter the appearance and underlying statistics of the carrier. 
The amount of alteration depends on:
The amount of hidden message, which is measured by bits-per-pixel(bpp).
The carrier itself. 



In pseudocode, convolution is (ignoring boundary conditions):
for w in 1..W
  for h in 1..H
    for x in 1..K
      for y in 1..K
        for m in 1..M
          for d in 1..D
            output(w, h, m) += input(w+x, h+y, d) * filter(m, x, y, d)
          end
        end
      end
    end
  end
end