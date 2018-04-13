N = 10;         % sample number;
u = 0.33;       % expectation of sample
iter = 1000;    % iterate times

uPrior = 0:0.01:1;
result = zeros(1,iter);
a = 30/2;b = 30/2;
for it = 2:iter
    m = sum(rand(1,N)<u);
    l = N - m;
    m = m/(it*N)*30;
    l = l/(it*N)*30;
    a = a/it*(it-1);
    b = b/it*(it-1);
    
    a = a+m;
    b = b+l;
    sumab = a + b;
    a = a/sumab*30;b = b/sumab*30;
    
    beta = gamma(a+b)./( gamma(a).*gamma(b) ).* ( uPrior.^(a-1)) .* ( (1-uPrior).^(b-1));
    beta = beta/sum(beta(:));
    
	plot(uPrior,beta);
	result(it) = a/(a+b);
	pause;
end;
plot(result);
result(end)