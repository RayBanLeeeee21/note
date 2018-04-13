dx	= 0.01;		dy 	= 0.01;
x	= -5:dx:5;	y 	= -5:dy:5;
ux	= 0;    	uy	= 0;
std_d_x = 1;		std_d_y = 1;


[X,Y] = meshgrid(x,y);
Z = 1/ (2*pi*std_d*std_d).*exp( -1/(2*std_d_x*std_d_x).*(X - ux).^2  - 1/(2*std_d_y*std_d_y).*(Y - uy).^2 );
area = sum( Z(:) )*dx*dy
mesh(X,Y,Z)