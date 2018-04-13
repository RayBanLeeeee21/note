x = [randn(20,1) + (1:20)',ones(20,1);];
y = randn(20,1) + (1:20)';

% 核心算法  
w = ( (x'*x)^-1 )*x'*y;
y_ = x*w;

scatter(x(:,1),y);hold on;
plot(x(:,1),y_,'r')
