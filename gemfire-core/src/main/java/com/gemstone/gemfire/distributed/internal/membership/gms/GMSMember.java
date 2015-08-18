/*=========================================================================
 * Copyright (c) 2010-2014 Pivotal Software, Inc. All Rights Reserved.
 * This product is protected by U.S. and international copyright
 * and intellectual property laws. Pivotal products are covered by
 * one or more patents listed at http://www.pivotal.io/patents.
 *=========================================================================
 */
package com.gemstone.gemfire.distributed.internal.membership.gms;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.jgroups.util.UUID;

import com.gemstone.gemfire.DataSerializer;
import com.gemstone.gemfire.distributed.DurableClientAttributes;
import com.gemstone.gemfire.distributed.internal.DistributionManager;
import com.gemstone.gemfire.distributed.internal.membership.MemberAttributes;
import com.gemstone.gemfire.distributed.internal.membership.NetMember;
import com.gemstone.gemfire.internal.DataSerializableFixedID;
import com.gemstone.gemfire.internal.InternalDataSerializer;
import com.gemstone.gemfire.internal.Version;
import com.gemstone.gemfire.internal.i18n.LocalizedStrings;

/**
 * This is the fundamental representation of a member of a GemFire distributed system.
 * 
 * Unfortunately, this class serves two distinct functions.  First, it is the
 * fundamental element of membership in the GemFire distributed system.  As such,
 * it is used in enumerations and properly responds to hashing and equals() comparisons.
 * 
 * Second, it is used as a cheap way of representing an address.  This is
 * unfortunate, because as a NetMember, it holds two separate port numbers: the
 * "membership" descriptor as well as a direct communication channel.
 * 
 */
public class GMSMember implements NetMember, DataSerializableFixedID {
  // whether to show UUID info in toString()
  private final static boolean SHOW_UUIDS = Boolean.getBoolean("gemfire.show_UUIDs");
  
  private int udpPort=0;
  private boolean preferredForCoordinator;
  private boolean splitBrainEnabled;
  private byte memberWeight;
  private InetAddress inetAddr;
  private int processId;
  private int vmKind;
  private int vmViewId = -1;
  private int directPort;
  private String name;
  private DurableClientAttributes durableClientAttributes;
  private String[] groups;
  private short versionOrdinal;
  private long uuidLSBs;
  private long uuidMSBs;
  
  
  
  // Used only by Externalization
  public GMSMember() {
  }
  
  public MemberAttributes getAttributes() {
    return new MemberAttributes(directPort, processId,
        vmKind, vmViewId, name, groups,
        durableClientAttributes);
  }

  public void setAttributes(MemberAttributes p_attr) {
    MemberAttributes attr = p_attr;
    if (attr == null) {
      attr = MemberAttributes.INVALID;
    }
    processId = attr.getVmPid();
    vmKind = attr.getVmKind();
    directPort = attr.getPort();
    vmViewId = attr.getVmViewId();
    name = attr.getName();
    groups = attr.getGroups();
    durableClientAttributes = attr.getDurableClientAttributes();
  }
  
  /**
   * This is the only constructor to refer to a CacheMember other
   * than the current host.
   */
  public GMSMember(GMSMember m) {
    udpPort=m.udpPort;
    preferredForCoordinator=m.preferredForCoordinator;
    splitBrainEnabled=m.splitBrainEnabled;
    memberWeight=m.memberWeight;
    inetAddr=m.inetAddr;
    processId=m.processId;
    vmKind=m.vmKind;
    vmViewId=m.vmViewId;
    directPort=m.directPort;
    name=m.name;
    durableClientAttributes=m.durableClientAttributes;
    groups=m.groups;
    versionOrdinal=m.versionOrdinal;
    uuidLSBs=m.uuidLSBs;
    uuidMSBs=m.uuidMSBs;
  }

  /**
   * Create a CacheMember referring to the current host (as defined by
   * the given string).
   * 
   * @param i the hostname, must be for the current host
   * @param p the membership listening port
   */
  public GMSMember(String i, int p) {
    udpPort=p;
    try {
      inetAddr=InetAddress.getByName(i);
    } catch (UnknownHostException e) {
      // oops
    }
  }

  /**
   * Create a CacheMember referring to the current host (as defined by
   * the given string).
   * 
   * @param i the hostname, must be for the current host
   * @param p the membership listening port
   * @param splitBrainEnabled whether the member has network partition detection enabled
   * @param preferredForCoordinator whether the member can be group coordinator
   * @param version the member's version ordinal
   * @param msbs - most significant bytes of UUID
   * @param lsbs - least significant bytes of UUID
   */
  public GMSMember(MemberAttributes attr, InetAddress i, int p, boolean splitBrainEnabled, boolean preferredForCoordinator,
      short version,
      long msbs, long lsbs) {
    setAttributes(attr);
    this.inetAddr = i;
    this.udpPort=p;
    this.splitBrainEnabled = splitBrainEnabled;
    this.preferredForCoordinator = preferredForCoordinator;
    this.versionOrdinal = version;
    this.uuidMSBs = msbs;
    this.uuidLSBs = lsbs;
  }

  public int getPort() {
    return this.udpPort;
  }

  public boolean isMulticastAddress() {
    return false;  //ipAddr.isMulticastAddress();
  }
  
  public boolean splitBrainEnabled() {
    return this.splitBrainEnabled;
  }
  
  public boolean preferredForCoordinator() {
    return this.preferredForCoordinator;
  }
  
  public void setPreferredForCoordinator(boolean preferred) {
    this.preferredForCoordinator = preferred;
  }
  
  public InetAddress getInetAddress() {
    return this.inetAddr;
  }
  
  public short getVersionOrdinal() {
    return this.versionOrdinal;
  }
  
  public void setVersionOrdinal(short versionOrdinal) {
    this.versionOrdinal = versionOrdinal;
  }
  
  public void setUUID(UUID u) {
    this.uuidLSBs = u.getLeastSignificantBits();
    this.uuidMSBs = u.getMostSignificantBits();
  }
  
  /**
   * return the jgroups logical address for this member,
   * if it's been established
   */
  public UUID getUUID() {
    if (this.uuidLSBs == 0 && this.uuidMSBs == 0) {
      return null;
    }
    return new UUID(this.uuidMSBs, this.uuidLSBs);
  }
  
  public long getUuidMSBs() {
    return this.uuidMSBs;
  }
  
  public long getUuidLSBs() {
    return this.uuidLSBs;
  }

  /**
   * Establishes an order between 2 addresses. Assumes other contains non-null IpAddress.
   * Excludes channel_name from comparison.
   * @return 0 for equality, value less than 0 if smaller, greater than 0 if greater.
   */
  public int compare(NetMember other) {
    return compareTo(other);
  }

  /**
   * implements the java.lang.Comparable interface
   * @see java.lang.Comparable
   * @param o - the Object to be compared
   * @return a negative integer, zero, or a positive integer as this object is less than,
   *         equal to, or greater than the specified object.
   * @exception java.lang.ClassCastException - if the specified object's type prevents it
   *            from being compared to this Object.
   */
  public int compareTo(Object o) {
    if (o == this) {
      return 0;
    }
    // obligatory type check
    if ((o == null) || !(o instanceof GMSMember)) {
      throw new ClassCastException(LocalizedStrings.JGroupMember_JGROUPMEMBERCOMPARETO_COMPARISON_BETWEEN_DIFFERENT_CLASSES.toLocalizedString());
    }
    byte[] myAddr = inetAddr.getAddress();
    GMSMember his = (GMSMember)o;
    byte[] hisAddr = his.inetAddr.getAddress();
    if (myAddr != hisAddr) {
      for (int idx=0; idx < myAddr.length; idx++) {
        if (idx > hisAddr.length) {
          return 1;
        } else if (myAddr[idx] > hisAddr[idx]) {
          return 1;
        } else if (myAddr[idx] < hisAddr[idx]) {
          return -1;
        }
      }
    }
    if (udpPort < his.udpPort) return -1;
    if (his.udpPort < udpPort) return 1;
    int result = 0;

    // bug #41983, address of kill-9'd member is reused
    // before it can be ejected from membership
    if (result == 0) {
      if (this.vmViewId >= 0 && his.vmViewId >= 0) {
        if (this.vmViewId < his.vmViewId) {
          result = -1;
        } else if (his.vmViewId < this.vmViewId) {
          result = 1;
        }
      } else if (this.processId != 0 && his.processId != 0) {
        // starting in 8.0 we also consider the processId.  During startup
        // we may have a message from a member that hasn't finished joining
        // and address canonicalization may find an old address that has
        // the same addr:port.  Since the new member doesn't have a viewId
        // its address will be equal to the old member's address unless
        // we also pay attention to the processId.
        if (this.processId < his.processId){
          result = -1;
        } else if (his.processId < this.processId) {
          result = 1;
        }
      }
    }
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    // GemStone fix for 29125
    if ((obj == null) || !(obj instanceof GMSMember)) {
      return false;
    }
    return compareTo(obj) == 0;
  }

  @Override
  public int hashCode() {
    if (this.inetAddr == null) {
      return this.udpPort;
    }
    return this.udpPort + inetAddr.hashCode();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(100);
    String uuid = SHOW_UUIDS? (";uuid=" + getUUID().toStringLong()) 
        : ((this.uuidLSBs == 0 && this.uuidMSBs == 0)? "; no uuid" : "; uuid set");

    sb.append("GMSMember[addr=").append(inetAddr).append(";port=").append(udpPort)
      .append(";processId=").append(processId).append(";name=").append(name)
      .append(uuid)
      .append("]");
    return sb.toString();
  }

  
  public int getUdpPort() {
    return udpPort;
  }

  public boolean isSplitBrainEnabled() {
    return splitBrainEnabled;
  }

  public byte getMemberWeight() {
    return memberWeight;
  }

  public InetAddress getInetAddr() {
    return inetAddr;
  }

  public int getProcessId() {
    return processId;
  }

  public int getVmKind() {
    return vmKind;
  }

  public int getVmViewId() {
    return vmViewId;
  }

  public int getDirectPort() {
    return directPort;
  }

  public String getName() {
    return name;
  }

  public DurableClientAttributes getDurableClientAttributes() {
    return durableClientAttributes;
  }

  public String[] getRoles() {
    return groups;
  }

  public void setUdpPort(int udpPort) {
    this.udpPort = udpPort;
  }

  public void setSplitBrainEnabled(boolean splitBrainEnabled) {
    this.splitBrainEnabled = splitBrainEnabled;
  }

  public void setMemberWeight(byte memberWeight) {
    this.memberWeight = memberWeight;
  }

  public void setInetAddr(InetAddress inetAddr) {
    this.inetAddr = inetAddr;
  }

  public void setProcessId(int processId) {
    this.processId = processId;
  }

  public void setVmKind(int vmKind) {
    this.vmKind = vmKind;
  }

  public void setBirthViewId(int birthViewId) {
    this.vmViewId = birthViewId;
  }

  public void setDirectPort(int directPort) {
    this.directPort = directPort;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setDurableClientAttributes(
      DurableClientAttributes durableClientAttributes) {
    this.durableClientAttributes = durableClientAttributes;
  }

  public void setGroups(String[] groups) {
    this.groups = groups;
  }

  public void setPort(int p) {
    this.udpPort = p;
  }

  @Override
  public Version[] getSerializationVersions() {
    return null;
  }

  @Override
  public int getDSFID() {
    return GMSMEMBER;
  }

  static final int SB_ENABLED = 0x01;
  static final int PREFERRED_FOR_COORD = 0x02;
  
  @Override
  public void toData(DataOutput out) throws IOException {
    Version.writeOrdinal(out, this.versionOrdinal, true);
    
    int flags = 0;
    if (splitBrainEnabled) flags |= SB_ENABLED;
    if (preferredForCoordinator) flags |= PREFERRED_FOR_COORD;
    out.writeInt(flags);

    DataSerializer.writeInetAddress(inetAddr, out);
    out.writeInt(udpPort);
    out.writeInt(vmViewId);
    out.writeInt(directPort);
    out.writeByte(memberWeight);
    out.writeInt(processId);
    out.writeInt(vmKind);
    DataSerializer.writeString(name,  out);
    DataSerializer.writeStringArray(groups, out);
    out.writeLong(uuidMSBs);
    out.writeLong(uuidLSBs);
  }

  @Override
  public void fromData(DataInput in) throws IOException, ClassNotFoundException {
    this.versionOrdinal = Version.readOrdinal(in);
    
    int flags = in.readInt();
    this.splitBrainEnabled = (flags & SB_ENABLED) != 0;
    this.preferredForCoordinator = (flags & PREFERRED_FOR_COORD) != 0;
    
    this.inetAddr = DataSerializer.readInetAddress(in);
    this.udpPort = in.readInt();
    this.vmViewId = in.readInt();
    this.directPort = in.readInt();
    this.memberWeight = in.readByte();
    this.processId = in.readInt();
    this.vmKind = in.readInt();
    this.name = DataSerializer.readString(in);
    this.groups = DataSerializer.readStringArray(in);
    this.uuidMSBs = in.readLong();
    this.uuidLSBs = in.readLong();
  }

  @Override
  public void writeAdditionalData(DataOutput out) throws IOException {
    out.writeLong(uuidMSBs);
    out.writeLong(uuidLSBs);
  }

  @Override
  public void readAdditionalData(DataInput in) throws ClassNotFoundException,
      IOException {
    this.uuidMSBs = in.readLong();
    this.uuidLSBs = in.readLong();
  }
}
