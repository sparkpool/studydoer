package com.lexeme.web.service.user.impl;

import java.security.InvalidParameterException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.hibernate.Query;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lexeme.web.constants.MessageConstants;
import com.lexeme.web.domain.acl.Permissions;
import com.lexeme.web.domain.acl.Roles;
import com.lexeme.web.domain.user.User;
import com.lexeme.web.domain.user.UserToken;
import com.lexeme.web.enums.EnumTokenType;
import com.lexeme.web.pojo.user.UserPojo;
import com.lexeme.web.service.acl.IACLService;
import com.lexeme.web.service.email.IEmailManager;
import com.lexeme.web.service.user.IUserService;
import com.lexeme.web.service.user.IUserTokenService;
import com.lexeme.web.service.user.IUserValidationService;
import com.lexeme.web.util.LexemeUtil;

@Service
public class UserServiceImpl implements IUserService{

	private static final Logger logger = Logger.getLogger(UserServiceImpl.class);
	
	@Autowired
	private SessionFactory sessionFactory;

	@Autowired
	private IACLService aclService;

	@Autowired
	private IEmailManager emailManager;
	
	@Autowired
	private IUserTokenService userTokenService;
	
	@Autowired
	private IUserValidationService userValidationService;
	
	@Override
    @Transactional
	public UserPojo signupUser(UserPojo userPojo, String contextPath) throws NoSuchAlgorithmException {
		if(userPojo == null){
			throw new InvalidParameterException("USER POJO CAN NOT BE NULL");
		}
		User user = createUserFromPojo(userPojo);
		user.setRoles(getRolesForSignUp(userPojo.getRole()));
		String salt = LexemeUtil.getSalt();
		String password = LexemeUtil.getHashPassword(userPojo.getPassword(), salt);
		user.setPassword(password);
		user.setSalt(salt);
		Long id = (Long) sessionFactory.getCurrentSession().save(user);
		logger.info("Sign Up Of user from DB result is " + id);
		if(id!=null){
			userPojo.setId(id);
			user.setId(id);
			getEmailManager().sendSignUpEmail(user, getUserTokenService().insertNewUserTokenAndReturnActivationLink(user, contextPath));
		}
		return userPojo;
	}
	
	private Set<Roles> getRolesForSignUp(String role){
		Set<Roles> roles = getAclService().getRolesForSignUp(role);
		if(roles == null || roles.size() == 0){
			throw new InvalidParameterException("INVALID ROLE FROM DB");
		}
		return roles;
	}
	
	private User createUserFromPojo(UserPojo userPojo){
		User user = new User();
		user.setEmail(userPojo.getEmail());
		user.setUserName(userPojo.getUserName());
		user.setCreateDt(new Date());
		return user;
	}

	public SessionFactory getSessionFactory() {
		return sessionFactory;
	}

	@Override
	@Transactional
	public UserPojo login(UserPojo userPojo) throws NoSuchAlgorithmException {
		if(userPojo == null){
			throw new InvalidParameterException("USER POJO CAN NOT BE NULL");
		}
		
		User user = getUserValidationService().validateUser(userPojo.getEmail());
		if(user == null){
		   userPojo.setMsg(MessageConstants.INVALID_EMAIL);	
		   return userPojo;
		}
		String password = LexemeUtil.getHashPassword(userPojo.getPassword(), user.getSalt());
		
		Query query = getSessionFactory().getCurrentSession().getNamedQuery("USER.LOGIN")
		.setString("email", userPojo.getEmail()).setString("password", password);
		
		user = (User) query.uniqueResult();
		if(user!=null && user.getId()!=null){
			userPojo.setId(user.getId());
			loginUser(userPojo.getEmail(), user.getPassword());
		}else{
			userPojo.setMsg(MessageConstants.INVALID_PASSWORD);
		}
		return userPojo;
	}
	
	private void loginUser(String email, String password){
		UsernamePasswordToken token = new UsernamePasswordToken(email, password);
		token.setRememberMe(true);
		SecurityUtils.getSubject().login(token);
	}

	public IACLService getAclService() {
		return aclService;
	}

	public IEmailManager getEmailManager() {
		return emailManager;
	}

	public IUserTokenService getUserTokenService() {
		return userTokenService;
	}

	@Override
	@Transactional
	public boolean setPassword(String password, String userId) throws NoSuchAlgorithmException {
		Long userIdLng = Long.parseLong(userId.trim());
		User user = getUserById(userIdLng);
		if(user != null){
			String salt = LexemeUtil.getSalt();
			String hashOfPassword = LexemeUtil.getHashPassword(password, salt);
			user.setPassword(hashOfPassword);
			user.setSalt(salt);
			Long id = (Long)getSessionFactory().getCurrentSession().save(user);
			if(id !=null){
				UserToken userToken = getUserTokenFromUserId(userIdLng, EnumTokenType.FP);
				getSessionFactory().getCurrentSession().delete(userToken);
				return true;
			}
		}
		return false;
	}
	
	private UserToken getUserTokenFromUserId(Long userId, EnumTokenType enumTokenType){
		Query query = getSessionFactory().getCurrentSession().getNamedQuery("GET.USER.TOKEN").
		   setLong("userId", userId).setLong("tokenTypeId", enumTokenType.getTokenTypeId());
		return (UserToken)query.uniqueResult();
	}
	
	@Override
	public User getUserById(Long userId){
		Query query = getSessionFactory().getCurrentSession().getNamedQuery("USERID.VALIDATE").
		setLong("id", userId);
		return (User)query.uniqueResult();
	}
	
	@Override
	@Transactional(readOnly = true)
	public Map<String, Set<String>> getRolesAndPermission(Long userId){
		Map<String, Set<String>> map = new HashMap<String, Set<String>>();
		User user = getUserById(userId);
		if(user!=null){
			Set<String> roles = getStringRoles(user.getRoles());
			logger.info("Roles are " + roles);
			map.put("roles",roles);
			Set<String> permissions = getStringPermissions(user.getRoles());
			map.put("permissions", permissions);
		}
		return map;
	}
	
	private Set<String> getStringRoles(Set<Roles> roles){
		Set<String> strRoles = new HashSet<String>();
		if(roles!=null){
			for(Roles role : roles){
				strRoles.add(role.getName());
			}	
		}
		return strRoles;
	}
	
	private Set<String> getStringPermissions(Set<Roles> roles){
		Set<String> strPermissions = new HashSet<String>();
		if(roles!=null){
			for(Roles role : roles){
				if(role!=null && role.getPermissions()!=null){
					for(Permissions permission : role.getPermissions()){
						strPermissions.add(permission.getName());
					}
				}
			}
		}
		return strPermissions;
	}

	public IUserValidationService getUserValidationService() {
		return userValidationService;
	}

	@Override
	public void logout() {
		SecurityUtils.getSubject().logout();
	}

}
