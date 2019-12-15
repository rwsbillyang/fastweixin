package com.github.sd4324530.fastweixin.api.response;

import com.alibaba.fastjson.annotation.JSONField;

/**
 * @author peiyu
 */
public class GetUserInfoResponse extends BaseResponse {

    private Integer subscribe;
    private String  openid;
    private String  nickname;
    private Integer sex;
    private String  language;
    private String  city;
    private String  province;
    private String  country;
    private String  headimgurl;
    @JSONField(name = "subscribe_time")
    private Long    subscribeTime;
    private String  unionid;
    private String remark;
    private Integer groupid;

	/**
	 * 返回用户关注的渠道来源，
	 * ADD_SCENE_SEARCH 公众号搜索，
	 * ADD_SCENE_ACCOUNT_MIGRATION 公众号迁移，
	 * ADD_SCENE_PROFILE_CARD 名片分享，
	 * ADD_SCENE_QR_CODE 扫描二维码，
	 * ADD_SCENE_PROFILE_ LINK 图文页内名称点击，
	 * ADD_SCENE_PROFILE_ITEM 图文页右上角菜单，
	 * ADD_SCENE_PAID 支付后关注，
	 * ADD_SCENE_OTHERS 其他
	 * https://developers.weixin.qq.com/doc/offiaccount/User_Management/Get_users_basic_information_UnionID.html#UinonId
	 * */
    @JSONField(name = "subscribe_scene")
    private String    subscribeScene;


	/**
     * 二维码扫码场景（开发者自定义）
     * */
    @JSONField(name = "qr_scene")
    private Integer qrScene;
    
    /**
     * 二维码扫码场景描述（开发者自定义）
     * */
    @JSONField(name = "qr_scene_str")
    private String qrSceneStr;
    
    public Integer getSubscribe() {
        return subscribe;
    }

    public void setSubscribe(Integer subscribe) {
        this.subscribe = subscribe;
    }

    public String getOpenid() {
        return openid;
    }

    public void setOpenid(String openid) {
        this.openid = openid;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public Integer getSex() {
        return sex;
    }

    public void setSex(Integer sex) {
        this.sex = sex;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getHeadimgurl() {
        return headimgurl;
    }

    public void setHeadimgurl(String headimgurl) {
        this.headimgurl = headimgurl;
    }

    public Long getSubscribeTime() {
        return subscribeTime;
    }

    public void setSubscribeTime(Long subscribeTime) {
        this.subscribeTime = subscribeTime;
    }

    public String getUnionid() {
        return unionid;
    }

    public void setUnionid(String unionid) {
        this.unionid = unionid;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Integer getGroupid() {
		return groupid;
	}

	public void setGroupid(Integer groupid) {
		this.groupid = groupid;
	}
	
    
    public String getSubscribeScene() {
		return subscribeScene;
	}

	public void setSubscribeScene(String subscribeScene) {
		this.subscribeScene = subscribeScene;
	}

	public Integer getQrScene() {
		return qrScene;
	}

	public void setQrScene(Integer qrScene) {
		this.qrScene = qrScene;
	}

	public String getQrSceneStr() {
		return qrSceneStr;
	}

	public void setQrSceneStr(String qrSceneStr) {
		this.qrSceneStr = qrSceneStr;
	}
}
