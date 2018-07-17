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



## Abstract

* *[problem]* : visual communication is one-sided.
    * // We can **easily perceive** information in the visual form
    * // but is hard for us to **express ourselves visually**. 
    * *[e.g.]* shoes shop
    * *[current method]* any non-professional edit will lead to **unrealistic**; 
        * // i.e. classic visual manipulation does not prevent the user "fall off" the **manifold of natural images**.


*  *[problem]*: GAN cannot be used in practical applications:
    1. still not quite **photo-realistic**, **low resolution**
    2. The GAN models usually **generate images from a latent vector**, which made the generation **hard to be controlled by user**.

* *[method]*: Use the GAN to learn the manifold of natural images, 
    * to **constrain the output** of manipulations, 
    * instead of **generate images**.

* *[contribution]*:
    1. **manipulating an existing photo** based on an underlying generative model
    2. "Generative transformation" of **one image to look more like another**.
    3. Generate a new image form **scratch based on user's scribbles**.  

## prior work

* **Image editing and user interaction**:
    * Image editing:
        * change the color properties
        * image warping
        * intelligently reshuffle the pixels in an image following user's edit.
    * *[problem]* Artifact: the reason is that these editing **rely on low-level principles** but do not capture **higher-level informations about natural images**.
        * unrealistic colors 
        * exaggerated stretching
        * obvious repetitions
        * over smoothing

* **Image morphing**: smooth visual transition between two input images


* **Natural image statistics**:

* **Neural generative models**: learn **a generative network** jointly with a second **discriminative adversarial network** in a **min-max** objective:
    1. The discriminator tries to distinguish between the **generated samples** and **natural image samples**
    2. The generator tries to **fool the discriminator**, producing highly **realistic looking images**.

    * GAN does not yield a **stable training objective**.
    * There is no tools to **change the generation process with intuitive user controls**.

## Learning the Natural Image Manifold

* **M**: ideal low-dimensional manifold of natural picture
* **S(x1,x2), x1,x2 belong to M**: distance which measures the perceptual similarity between x1, x2
* 