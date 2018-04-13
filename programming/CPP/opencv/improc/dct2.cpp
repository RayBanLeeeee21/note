#include<opencv2/opencv.hpp>
#include<opencv2/imgproc/imgproc.hpp>
using namespace cv;

void dct2(Mat src, Mat dst){

	void dct_anysize(Mat src, Mat dst);

	//	dst = dct ( dct (src)' )'
	dct_anysize(src, dst);
	dst = dst.t();
	dct_anysize(dst, dst);
	dst = dst.t();

}

void dct_anysize(Mat src, Mat dst){

	if (src.rows & 0x01){	//judge if rows is odd
		Mat tempSrc = Mat::zeros(src.rows, src.cols * 2, src.type());
		Mat tempDst = Mat::zeros(src.rows, src.cols * 2, src.type());

		src.copyTo(tempSrc.colRange(0, src.cols));
		flip(src, tempSrc.colRange(src.cols, src.cols * 2), 1);

		dct(tempSrc, tempDst, CV_DXT_ROWS);
		tempDst = tempDst / sqrt(2);
		for (int i = 0; i < src.cols; i++){
			tempDst.col(i<<1).copyTo(dst.col(i));
		}
	}
	else{
		dct(src, dst, CV_DXT_ROWS);
	}
}
