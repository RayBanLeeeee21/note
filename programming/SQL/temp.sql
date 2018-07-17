select 
	u1.username as user1,u2.username as user2 
from 
	user as u1, relationship as rl, user as u2 
where 
	rl.user1_id = u1.id and 
	rl.user2_id = u2.id;