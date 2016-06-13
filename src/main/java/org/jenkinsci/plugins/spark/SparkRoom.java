package org.jenkinsci.plugins.spark;

import java.io.Serializable;

import org.kohsuke.stapler.DataBoundConstructor;

public class SparkRoom implements Serializable {

	private static final long serialVersionUID = 1L;
	private String name;
	private String roomid;
	private String token;

	/**
	 * @param id
	 * @param roomid
	 *            spark room id
	 * @param token
	 *            bearer token
	 */
	@DataBoundConstructor
	public SparkRoom(String name, String roomid, String token) {
		this.name = name;
		this.roomid = roomid;
		this.token = token;
	}

	public SparkRoom() {
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getRoomid() {
		return roomid;
	}

	public void setRoomid(String roomid) {
		this.roomid = roomid;
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SparkRoom other = (SparkRoom) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "SparkRoom [name=" + name + ", room id=" + roomid + ", token=******]";
	}

}
