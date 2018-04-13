function c = convariance(x,y)

    lengthX = size(x,1);
    covXY = cov([x',y']);
    c = covXY(1:lengthX,(lengthX+1):(2*length);

end










% test:
%   x = rand(10,20);
%   y = rand(10,20);
%   meanX = mean(x')';
%   meanY = mean(y')';
%   cov1 = zeros(10,10);cov2 = cov1;
%   for n = 1:20
%       cov1 = cov1 + (x(:,n) - meanX) * (y(:,n) - meanY)'.*0.05;
%       cov2 = cov2 + x(:,n)* y(:,n)'.*0.05;
%   end;
%   cov2 = cov2 - meanX*meanY';
%   >> cov3 = cov([x',y'])